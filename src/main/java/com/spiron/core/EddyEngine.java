package com.spiron.core;

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
      return new EddyState(a.id(), a.vector(), newEnergy);
    } else {
      double damped = a.energy() * alpha;
      log.debug("Damped eddy {} -> {}", a.id(), damped);
      return new EddyState(a.id(), a.vector(), damped);
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
      if (rpcClient != null) rpcClient.commit(d);
      persistState(d);
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
  }

  public synchronized void persistState(EddyState eddy) {
    if (raftLog != null) {
      raftLog.append("commit:" + eddy.id() + "," + eddy.energy());
    }
    if (snapshotStore != null) {
      snapshotStore.save(eddy);
    }

    eddies.put(eddy.id(), eddy);
  }
}
