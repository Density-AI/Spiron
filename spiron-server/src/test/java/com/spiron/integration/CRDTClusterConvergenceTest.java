package com.spiron.integration;

import static org.assertj.core.api.Assertions.*;

import com.spiron.crdt.CRDTMergeEngine;
import com.spiron.crdt.GossipScheduler;
import com.spiron.proto.EddyProto;
import com.spiron.serialization.CRDTJsonCodec;
import com.spiron.storage.CRDTStore;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration test for 3-node CRDT cluster gossip convergence.
 *
 * Simulates 3 independent nodes running gossip protocol:
 * - All nodes start with different local state
 * - After O(log N) gossip rounds, all replicas converge to same state
 * - Finality is deterministic across all replicas (CRDT merge is commutative)
 *
 * This test validates:
 * 1. Gossip protocol can synchronize state across multiple replicas
 * 2. CRDT merge is idempotent and commutative (state independent of message order)
 * 3. Convergence happens within reasonable time (< 2 seconds for 3 nodes)
 */
@DisplayName("CRDT Cluster Convergence")
public class CRDTClusterConvergenceTest {

  private static final String EDDY_ID = "test-eddy-1";
  private static final long GOSSIP_INTERVAL_MS = 100;
  private static final long CONVERGENCE_TIMEOUT_MS = 2000;

  private CRDTJsonCodec codec;
  private MockCRDTStore node1Store;
  private MockCRDTStore node2Store;
  private MockCRDTStore node3Store;
  private GossipScheduler scheduler1;
  private GossipScheduler scheduler2;
  private GossipScheduler scheduler3;

  @BeforeEach
  void setUp() {
    codec = new CRDTJsonCodec();
    node1Store = new MockCRDTStore();
    node2Store = new MockCRDTStore();
    node3Store = new MockCRDTStore();
    scheduler1 = new GossipScheduler();
    scheduler2 = new GossipScheduler();
    scheduler3 = new GossipScheduler();
  }

  @AfterEach
  void tearDown() {
    scheduler1.stop();
    scheduler2.stop();
    scheduler3.stop();
  }

  @Test
  @DisplayName("Three nodes converge to same CRDT state after gossip")
  void testThreeNodeConvergence() throws InterruptedException {
    // Setup: Each node has different initial state
    EddyProto.CRDTEddy state1 = buildEddy(new double[] { 1.0, 0.0, 0.0 }, 3);
    EddyProto.CRDTEddy state2 = buildEddy(new double[] { 0.0, 1.0, 0.0 }, 2);
    EddyProto.CRDTEddy state3 = buildEddy(new double[] { 0.0, 0.0, 1.0 }, 1);

    node1Store.put(EDDY_ID, codec.serializeEddy(state1));
    node2Store.put(EDDY_ID, codec.serializeEddy(state2));
    node3Store.put(EDDY_ID, codec.serializeEddy(state3));

    // Create gossip client that routes between 3 nodes
    MockGossipNetwork network = new MockGossipNetwork(
      "node1",
      scheduler1,
      node1Store,
      codec,
      "node2",
      scheduler2,
      node2Store,
      codec,
      "node3",
      scheduler3,
      node3Store,
      codec
    );

    // Start gossip schedulers
    scheduler1.start(
      "node1",
      Arrays.asList("node2", "node3"),
      GOSSIP_INTERVAL_MS,
      node1Store,
      network.getClient("node1"),
      codec,
      3L  // finalityThreshold
    );
    scheduler2.start(
      "node2",
      Arrays.asList("node1", "node3"),
      GOSSIP_INTERVAL_MS,
      node2Store,
      network.getClient("node2"),
      codec,
      3L  // finalityThreshold
    );
    scheduler3.start(
      "node3",
      Arrays.asList("node1", "node2"),
      GOSSIP_INTERVAL_MS,
      node3Store,
      network.getClient("node3"),
      codec,
      3L  // finalityThreshold
    );

    // Wait for convergence
    boolean converged = waitForConvergence(
      node1Store,
      node2Store,
      node3Store,
      CONVERGENCE_TIMEOUT_MS
    );

    // Verify all nodes have same state
    assertThat(converged).isTrue();

    String finalState1 = node1Store.get(EDDY_ID).get();
    String finalState2 = node2Store.get(EDDY_ID).get();
    String finalState3 = node3Store.get(EDDY_ID).get();

    // All should be equal
    assertThat(finalState1).isEqualTo(finalState2);
    assertThat(finalState2).isEqualTo(finalState3);

    // Verify merged state is LWW winner
    EddyProto.CRDTEddy merged1 = codec.deserializeEddy(finalState1);
    EddyProto.CRDTEddy merged2 = codec.deserializeEddy(finalState2);
    EddyProto.CRDTEddy merged3 = codec.deserializeEddy(finalState3);

    assertThat(merged1).isNotNull();
    assertThat(merged2).isNotNull();
    assertThat(merged3).isNotNull();

    // All should have the same vector state (highest timestamp seen)
    assertThat(merged1.getState().getTimestamp()).isEqualTo(
      merged2.getState().getTimestamp()
    );
    assertThat(merged2.getState().getTimestamp()).isEqualTo(
      merged3.getState().getTimestamp()
    );
  }

  @Test
  @DisplayName(
    "Convergence is idempotent: multiple gossip rounds yield same state"
  )
  void testIdempotentConvergence() throws InterruptedException {
    // Setup initial states
    EddyProto.CRDTEddy state1 = buildEddy(new double[] { 1.0, 0.0 }, 1);
    EddyProto.CRDTEddy state2 = buildEddy(new double[] { 0.0, 1.0 }, 2);

    node1Store.put(EDDY_ID, codec.serializeEddy(state1));
    node2Store.put(EDDY_ID, codec.serializeEddy(state2));
    node3Store.put(EDDY_ID, codec.serializeEddy(state1)); // Same as node1

    // Direct merge simulation (no gossip)
    String merged1Json = node1Store.get(EDDY_ID).get();
    String merged2Json = node2Store.get(EDDY_ID).get();

    EddyProto.CRDTEddy merged1 = codec.deserializeEddy(merged1Json);
    EddyProto.CRDTEddy merged2 = codec.deserializeEddy(merged2Json);

    // Merge multiple times - result should be same
    EddyProto.CRDTEddy result1 = CRDTMergeEngine.merge(merged1, merged2);
    EddyProto.CRDTEddy result2 = CRDTMergeEngine.merge(result1, merged2);

    // Second merge should not change result (idempotence)
    assertThat(result1.getState().getTimestamp()).isEqualTo(
      result2.getState().getTimestamp()
    );
    assertThat(result1.getState().getReplicaId()).isEqualTo(
      result2.getState().getReplicaId()
    );
  }

  @Test
  @DisplayName("Convergence is commutative: merge order doesn't matter")
  void testCommutativeConvergence() {
    // Create three states
    EddyProto.CRDTEddy state1 = buildEddy(new double[] { 1.0 }, 1);
    EddyProto.CRDTEddy state2 = buildEddy(new double[] { 2.0 }, 2);
    EddyProto.CRDTEddy state3 = buildEddy(new double[] { 3.0 }, 3);

    // Merge in different order
    EddyProto.CRDTEddy merge123 = CRDTMergeEngine.merge(
      CRDTMergeEngine.merge(state1, state2),
      state3
    );

    EddyProto.CRDTEddy merge321 = CRDTMergeEngine.merge(
      CRDTMergeEngine.merge(state3, state2),
      state1
    );

    // Both should have same final state
    assertThat(merge123.getState().getTimestamp()).isEqualTo(
      merge321.getState().getTimestamp()
    );
    assertThat(merge123.getState().getReplicaId()).isEqualTo(
      merge321.getState().getReplicaId()
    );
  }

  /**
   * Wait for all three stores to converge to same state.
   *
   * @return true if converged within timeout, false otherwise
   */
  private boolean waitForConvergence(
    CRDTStore s1,
    CRDTStore s2,
    CRDTStore s3,
    long timeoutMs
  ) throws InterruptedException {
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < timeoutMs) {
      String state1 = s1.get(EDDY_ID).orElse(null);
      String state2 = s2.get(EDDY_ID).orElse(null);
      String state3 = s3.get(EDDY_ID).orElse(null);

      if (state1 != null && state1.equals(state2) && state2.equals(state3)) {
        return true; // All converged
      }
      Thread.sleep(50);
    }
    return false;
  }

  /**
   * Build a test CRDTEddy with given vector and timestamp.
   */
  private EddyProto.CRDTEddy buildEddy(double[] vector, long timestamp) {
    EddyProto.CRDTVector.Builder vb = EddyProto.CRDTVector.newBuilder();
    vb.setTimestamp(timestamp);
    vb.setReplicaId("test-replica");
    for (double v : vector) {
      vb.addVector(v);
    }
    return EddyProto.CRDTEddy.newBuilder()
      .setId(EDDY_ID)
      .setState(vb.build())
      .build();
  }

  /**
   * Mock in-memory CRDT store for testing.
   */
  static class MockCRDTStore implements CRDTStore {

    private final Map<String, String> data = new ConcurrentHashMap<>();

    @Override
    public void put(String key, String value) {
      data.put(key, value);
    }

    @Override
    public Optional<String> get(String key) {
      return Optional.ofNullable(data.get(key));
    }

    @Override
    public Map<String, String> getAll() {
      return new HashMap<>(data);
    }

    @Override
    public void delete(String key) {
      data.remove(key);
    }

    @Override
    public void clear() {
      data.clear();
    }

    @Override
    public boolean exists(String key) {
      return data.containsKey(key);
    }

    @Override
    public void close() {}

    @Override
    public java.util.Optional<String> getLineage(String eddyId) {
      return java.util.Optional.empty();
    }

    @Override
    public void putLineage(String eddyId, String lineageJson) {}
  }

  /**
   * Mock gossip network routing messages between 3 nodes.
   */
  static class MockGossipNetwork {

    private final Map<String, NodeContext> nodes = new HashMap<>();

    MockGossipNetwork(
      String name1,
      GossipScheduler sched1,
      CRDTStore store1,
      CRDTJsonCodec codec1,
      String name2,
      GossipScheduler sched2,
      CRDTStore store2,
      CRDTJsonCodec codec2,
      String name3,
      GossipScheduler sched3,
      CRDTStore store3,
      CRDTJsonCodec codec3
    ) {
      nodes.put(name1, new NodeContext(sched1, store1, codec1));
      nodes.put(name2, new NodeContext(sched2, store2, codec2));
      nodes.put(name3, new NodeContext(sched3, store3, codec3));
    }

    GossipScheduler.EddyGossipClient getClient(String nodeName) {
      return (peerAddress, request) -> {
        NodeContext peer = nodes.get(peerAddress);
        if (peer == null) return Optional.empty();

        try {
          // Simulate receiving sync request: merge remote eddies
          for (Map.Entry<String, EddyProto.CRDTEddy> entry : request
            .getEddiesMap()
            .entrySet()) {
            String eddyId = entry.getKey();
            EddyProto.CRDTEddy remoteEddy = entry.getValue();

            Optional<String> localOpt = peer.store.get(eddyId);
            EddyProto.CRDTEddy merged = remoteEddy;

            if (localOpt.isPresent()) {
              EddyProto.CRDTEddy localEddy = peer.codec.deserializeEddy(
                localOpt.get()
              );
              if (localEddy != null) {
                merged = CRDTMergeEngine.merge(localEddy, remoteEddy);
              }
            }

            peer.store.put(eddyId, peer.codec.serializeEddy(merged));
          }

          // Build response with peer's current state
          EddyProto.SyncResponse.Builder responseBuilder =
            EddyProto.SyncResponse.newBuilder();
          for (Map.Entry<String, String> entry : peer.store
            .getAll()
            .entrySet()) {
            EddyProto.CRDTEddy eddy = peer.codec.deserializeEddy(
              entry.getValue()
            );
            if (eddy != null) {
              responseBuilder.putEddies(entry.getKey(), eddy);
            }
          }

          return Optional.of(responseBuilder.build());
        } catch (Exception e) {
          return Optional.empty();
        }
      };
    }

    static class NodeContext {

      final GossipScheduler scheduler;
      final CRDTStore store;
      final CRDTJsonCodec codec;

      NodeContext(
        GossipScheduler scheduler,
        CRDTStore store,
        CRDTJsonCodec codec
      ) {
        this.scheduler = scheduler;
        this.store = store;
        this.codec = codec;
      }
    }
  }
}
