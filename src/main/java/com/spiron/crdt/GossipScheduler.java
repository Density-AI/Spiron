package com.spiron.crdt;

import com.spiron.proto.EddyProto;
import com.spiron.proto.EddyProto.SyncRequest;
import com.spiron.proto.EddyProto.SyncResponse;
import com.spiron.serialization.CRDTJsonCodec;
import com.spiron.storage.CRDTStore;
import com.spiron.metrics.EnergyMetrics;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gossip Scheduler: Implements pull-based peer-to-peer CRDT synchronization.
 *
 * Semantics:
 * - Periodically selects a random peer and sends a SyncRequest with local CRDT state.
 * - On receiving SyncResponse, merges remote eddies with local state (CRDT merge is idempotent).
 * - Uses random peer selection to achieve O(log N) convergence rounds.
 * - No leader/coordinator required; all replicas independently gossip.
 *
 * Usage:
 * scheduler.start(localNodeId, peers, syncInterval, crdtStore, gossipClient, codec)
 * scheduler.stop()
 */
public class GossipScheduler {

  private static final Logger log = LoggerFactory.getLogger(
    GossipScheduler.class
  );
  
  private EnergyMetrics metrics;
  private FinallityDetector finalityDetector;
  private long finalityThreshold;

  private String localNodeId;
  private List<String> peers;
  private long syncIntervalMs;
  private CRDTStore crdtStore;
  private EddyGossipClient gossipClient;
  private CRDTJsonCodec codec;
  private ScheduledExecutorService executor;
  private ScheduledFuture<?> gossipTask;
  private volatile boolean running = false;
  private Random random = new Random();

  public GossipScheduler() {
    this.finalityDetector = new FinallityDetector();
  }
  
  public GossipScheduler(EnergyMetrics metrics) {
    this.metrics = metrics;
    this.finalityDetector = new FinallityDetector();
  }

  /**
   * Initialize and start the gossip scheduler.
   *
   * @param localNodeId current node identifier
   * @param peers list of peer node addresses (host:port)
   * @param syncIntervalMs interval in ms between sync operations
   * @param crdtStore backing storage for CRDT state
   * @param gossipClient RPC client to send Sync requests
   * @param codec JSON codec for serialization
   * @param finalityThreshold approval threshold for finality detection
   */
  public void start(
    String localNodeId,
    List<String> peers,
    long syncIntervalMs,
    CRDTStore crdtStore,
    EddyGossipClient gossipClient,
    CRDTJsonCodec codec,
    long finalityThreshold
  ) {
    this.localNodeId = localNodeId;
    this.peers = new ArrayList<>(peers);
    this.syncIntervalMs = syncIntervalMs;
    this.crdtStore = crdtStore;
    this.gossipClient = gossipClient;
    this.codec = codec;
    this.finalityThreshold = finalityThreshold;

    this.executor = Executors.newScheduledThreadPool(1, r -> {
      Thread t = new Thread(r, "GossipScheduler-" + localNodeId);
      t.setDaemon(true);
      return t;
    });

    running = true;
    gossipTask = executor.scheduleAtFixedRate(
      this::gossipRound,
      0,
      syncIntervalMs,
      TimeUnit.MILLISECONDS
    );

    log.info(
      "Started gossip scheduler for node {}: interval={}ms, peers={}",
      localNodeId,
      syncIntervalMs,
      peers.size()
    );
  }

  /**
   * Stop the gossip scheduler.
   */
  public void stop() {
    if (!running) return;
    running = false;
    if (gossipTask != null) {
      gossipTask.cancel(false);
    }
    if (executor != null) {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
    log.info("Stopped gossip scheduler for node {}", localNodeId);
  }

  /**
   * Execute one gossip round: select random peer, send SyncRequest, merge response.
   */
  private void gossipRound() {
    try {
      if (peers.isEmpty()) {
        log.debug("No peers available for gossip");
        return;
      }

      // Select random peer
      String targetPeer = peers.get(random.nextInt(peers.size()));

      // Build SyncRequest with local CRDT state
      SyncRequest request = buildSyncRequest();

      // Send to peer
      log.debug("Sending gossip sync to peer {}", targetPeer);
      Optional<SyncResponse> responseOpt = gossipClient.sync(
        targetPeer,
        request
      );

      // Merge response
      if (responseOpt.isPresent()) {
        SyncResponse response = responseOpt.get();
        mergeRemoteEddies(response);
      }
    } catch (Exception e) {
      log.warn("Gossip round failed", e);
    }
  }

  /**
   * Builds a SyncRequest containing local CRDT state to send to a peer.
   */
  private SyncRequest buildSyncRequest() {
    SyncRequest.Builder builder = SyncRequest.newBuilder();
    builder.setSenderId(localNodeId);

    try {
      Map<String, String> allEddies = crdtStore.getAll();
      for (Map.Entry<String, String> entry : allEddies.entrySet()) {
        String eddyId = entry.getKey();
        String jsonState = entry.getValue();
        EddyProto.CRDTEddy eddy = codec.deserializeEddy(jsonState);
        if (eddy != null) {
          builder.putEddies(eddyId, eddy);
        }
      }
    } catch (Exception e) {
      log.warn("Failed to build sync request", e);
    }

    return builder.build();
  }

  /**
   * Merge remote eddies from SyncResponse with local state.
   *
   * @param response the SyncResponse from peer
   */
  private void mergeRemoteEddies(SyncResponse response) {
    Map<String, EddyProto.CRDTEddy> remoteEddies = response.getEddiesMap();

    for (Map.Entry<
      String,
      EddyProto.CRDTEddy
    > entry : remoteEddies.entrySet()) {
      String eddyId = entry.getKey();
      EddyProto.CRDTEddy remoteEddy = entry.getValue();

      try {
        // Get local state
        Optional<String> localJsonOpt = crdtStore.get(eddyId);
        EddyProto.CRDTEddy localEddy = null;
        if (localJsonOpt.isPresent()) {
          localEddy = codec.deserializeEddy(localJsonOpt.get());
        }

        // Merge with metrics tracking
        EddyProto.CRDTEddy merged;
        if (localEddy != null) {
          if (metrics != null) {
            metrics.recordMerge(() -> {});
          }
          merged = CRDTMergeEngine.merge(localEddy, remoteEddy);
        } else {
          merged = remoteEddy;
          if (metrics != null) {
            metrics.incCrdtIngest();
          }
        }

        // Persist merged state
        String mergedJson = serializeEddy(merged);
        crdtStore.put(eddyId, mergedJson);

        // Check for finality
        Optional<Long> finality = finalityDetector.checkFinality(eddyId, merged, finalityThreshold);
        if (finality.isPresent()) {
          log.info("Eddy {} reached finality after gossip merge: cumulative approvals={}", 
            eddyId, finality.get());
        }

        log.debug("Merged eddy {} from gossip", eddyId);
      } catch (Exception e) {
        log.warn("Failed to merge eddy {}", eddyId, e);
      }
    }
  }

  /**
   * Serialize Eddy to JSON string.
   *
   * @param eddy the eddy to serialize
   * @return JSON string
   */
  private String serializeEddy(EddyProto.CRDTEddy eddy) {
    return codec.serializeEddy(eddy);
  }

  /**
   * Check if gossip is running.
   *
   * @return true if scheduler is active
   */
  public boolean isRunning() {
    return running;
  }

  /**
   * Interface for RPC client to send Sync requests to peers.
   */
  public interface EddyGossipClient {
    /**
     * Send a Sync request to a peer.
     *
     * @param peerAddress peer host:port
     * @param request the sync request
     * @return response if successful, empty on failure
     */
    Optional<SyncResponse> sync(String peerAddress, SyncRequest request);
  }
}
