package com.spiron.core;

import com.spiron.metrics.EnergyMetrics;
import com.spiron.metrics.StorageMetrics;
import com.spiron.metrics.ThroughputMetrics;
import com.spiron.network.RpcClient;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements Eddy dominance mechanics: energy merging, damping, and commit detection.
 */
public class EddyEngine {

  private static final Logger log = LoggerFactory.getLogger(EddyEngine.class);

  private final double alpha;
  private final double siphonFactor;
  private final double angularThreshold;
  private final double commitEnergy;
  private SpironRaftLog raftLog;
  private SpironSnapshotStore snapshotStore;
  private RpcClient rpcClient;
  private LineageTracker lineageTracker;
  private com.spiron.metrics.EnergyMetrics energyMetrics;
  private com.spiron.metrics.ThroughputMetrics throughputMetrics;
  private com.spiron.metrics.StorageMetrics storageMetrics;

  private final Map<String, EddyState> eddies = new HashMap<>();

  public EddyEngine(
    double alpha,
    double siphonFactor,
    double angularThreshold,
    double commitEnergy
  ) {
    this.alpha = alpha;
    this.siphonFactor = siphonFactor;
    this.angularThreshold = angularThreshold;
    this.commitEnergy = commitEnergy;
  }

  public synchronized void ingest(EddyState incoming) {
    eddies.merge(incoming.id(), incoming, this::merge);
    // Record energy level for all ingested eddies
    if (energyMetrics != null) {
      energyMetrics.recordEnergy(incoming.energy());
      energyMetrics.incCrdtIngest();
    }
    // Record throughput metrics
    if (throughputMetrics != null) {
      throughputMetrics.incEddiesIngested();
      long bytes = incoming.vector().length * 8L; // 8 bytes per double
      throughputMetrics.recordBytesIngested(bytes);
    }
    // Update state store size
    if (storageMetrics != null) {
      storageMetrics.setStateEntries(eddies.size());
    }
  }

  private EddyState merge(EddyState a, EddyState b) {
    double sim = EddyMath.angularSimilarity(a.vector(), b.vector());
    if (sim > angularThreshold) {
      double newEnergy = EddyMath.siphon(
        a.energy(),
        b.energy(),
        sim,
        siphonFactor
      );
      log.debug(
        "Merged eddies {} and {} sim={} energy={}",
        a.id(),
        b.id(),
        sim,
        newEnergy
      );
      // Record merge event and energy level
      if (energyMetrics != null) {
        energyMetrics.recordMerge(() -> {
          energyMetrics.incMerge();
          energyMetrics.recordEnergy(newEnergy);
        });
      }
      // Set parentId to the id of the eddy being merged from (b)
      return new EddyState(a.id(), a.vector(), newEnergy, b.id());
    } else {
      double damped = a.energy() * alpha;
      log.debug("Damped eddy {} -> {}", a.id(), damped);
      // Record damped energy level and damping event
      if (energyMetrics != null) {
        energyMetrics.recordEnergy(damped);
        energyMetrics.incCrdtDamping();
      }
      // Keep parentId unchanged (a.parentId)
      return new EddyState(a.id(), a.vector(), damped, a.parentId());
    }
  }

  public synchronized Optional<EddyState> dominant() {
    return eddies
      .values()
      .stream()
      .max(Comparator.comparingDouble(EddyState::energy))
      .filter(e -> e.energy() >= commitEnergy);
  }

  public synchronized Optional<EddyState> checkAndCommit() {
    var dom = dominant();
    dom.ifPresent(d -> {
      // Record lineage before commit
      if (lineageTracker != null) {
        lineageTracker.recordLineage(d.id(), d.parentId());
        log.info("Recorded lineage for dominant eddy: {}", d.id());
      }
      
      // Record commit latency and CRDT commit counter
      if (energyMetrics != null) {
        energyMetrics.recordCommit(() -> {
          energyMetrics.incCrdtCommit();
          if (rpcClient != null) rpcClient.commit(d);
          persistState(d);
        });
      } else {
        if (rpcClient != null) rpcClient.commit(d);
        persistState(d);
      }
      // Record emitted bytes
      if (throughputMetrics != null) {
        throughputMetrics.incEddiesEmitted();
        long bytes = d.vector().length * 8L;
        throughputMetrics.recordBytesEmitted(bytes);
      }
    });
    return dom;
  }

  public synchronized List<EddyState> snapshot() {
    return List.copyOf(eddies.values());
  }

  public void attachNetwork(RpcClient client) {
    this.rpcClient = client;
  }

  public void propagate(EddyState s) {
    if (rpcClient != null) rpcClient.broadcast(s);
  }

  public void attachStorage(SpironRaftLog log, SpironSnapshotStore store) {
    this.raftLog = log;
    this.snapshotStore = store;
    // Attempt recovery from snapshot on startup
    recoverFromSnapshot();
  }
  
  public void attachLineageTracker(LineageTracker tracker) {
    this.lineageTracker = tracker;
    log.info("Attached LineageTracker for persistent ancestry tracking");
  }
  
  /**
   * Recover state from snapshot store if available.
   * Called automatically when storage is attached.
   */
  private synchronized void recoverFromSnapshot() {
    if (snapshotStore == null) return;
    
    snapshotStore.load().ifPresent(recovered -> {
      log.info("Recovering from snapshot: eddy={}, energy={}", 
        recovered.id(), recovered.energy());
      eddies.put(recovered.id(), recovered);
      if (energyMetrics != null) {
        energyMetrics.recordEnergy(recovered.energy());
      }
    });
  }
  
  /**
   * Replay commit log for audit verification.
   * Returns count of replayed entries.
   * Note: Currently for audit only, does not restore full state.
   */
  public synchronized int replayLog() {
    if (raftLog == null) return 0;
    
    var entries = raftLog.readAll();
    log.info("Replaying {} log entries for audit verification", entries.size());
    
    int replayed = 0;
    for (String entry : entries) {
      // Log entries are prefixed with timestamp: "2025-01-16T12:34:56.789Z commit:..."
      if (entry.contains("commit:")) {
        replayed++;
        // Could parse and verify commit history here
        log.debug("Log entry: {}", entry);
      }
    }
    
    return replayed;
  }

  public void attachMetrics(EnergyMetrics metrics) {
    this.energyMetrics = metrics;
  }
  
  public void attachThroughputMetrics(ThroughputMetrics metrics) {
    this.throughputMetrics = metrics;
  }
  
  public void attachStorageMetrics(StorageMetrics metrics) {
    this.storageMetrics = metrics;
  }

  public synchronized void persistState(EddyState eddy) {
    if (storageMetrics != null) {
      storageMetrics.recordWrite(() -> {
        if (raftLog != null) {
          raftLog.append("commit:" + eddy.id() + "," + eddy.energy());
        }
        if (snapshotStore != null) {
          snapshotStore.save(eddy);
        }
      });
      storageMetrics.incWriteOps();
      long bytes = eddy.vector().length * 8L + 100; // vector + metadata
      storageMetrics.recordBytesWritten(bytes);
    } else {
      if (raftLog != null) {
        raftLog.append("commit:" + eddy.id() + "," + eddy.energy());
      }
      if (snapshotStore != null) {
        snapshotStore.save(eddy);
      }
    }

    eddies.put(eddy.id(), eddy);
  }
}
