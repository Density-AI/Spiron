package com.spiron.examples;

import com.spiron.crdt.CRDTMergeEngine;
import com.spiron.proto.EddyProto;
import com.spiron.serialization.CRDTJsonCodec;
import java.util.*;

/**
 * Example: Gossip Cluster Simulation
 *
 * Demonstrates a simplified gossip-based cluster without actual networking:
 * - 3 replicas with independent CRDT state
 * - Simulate peer-to-peer synchronization
 * - Show how state converges without a central coordinator
 *
 * This is a simplified example. Production systems would use:
 * - GossipScheduler for periodic gossip rounds
 * - EddyGossipService for RPC endpoints
 * - CRDTStore for persistence
 *
 * Run: java com.spiron.examples.GossipClusterExample
 */
public class GossipClusterExample {

  static class Replica {
    final String id;
    final Map<String, EddyProto.CRDTEddy> state = new HashMap<>();
    final CRDTJsonCodec codec = new CRDTJsonCodec();

    Replica(String id) {
      this.id = id;
    }

    public void merge(String eddyId, EddyProto.CRDTEddy remote) {
      EddyProto.CRDTEddy local = state.getOrDefault(eddyId, null);
      EddyProto.CRDTEddy merged = local != null ?
        CRDTMergeEngine.merge(local, remote) : remote;
      state.put(eddyId, merged);
    }

    public Map<String, EddyProto.CRDTEddy> getState() {
      return new HashMap<>(state);
    }

    public void putState(String eddyId, EddyProto.CRDTEddy eddy) {
      state.put(eddyId, eddy);
    }
  }

  public static void main(String[] args) throws InterruptedException {
    System.out.println("=== Gossip Cluster Example (3 nodes) ===\n");

    // Create 3 replicas
    Replica r1 = new Replica("replica-1");
    Replica r2 = new Replica("replica-2");
    Replica r3 = new Replica("replica-3");

    String eddyId = "shared-eddy-1";

    // Each replica starts with different state
    System.out.println("0. Initial state (each replica different):");
    EddyProto.CRDTEddy state1 = buildEddy(eddyId, "replica-1", 100L, new double[]{1.0, 0.0, 0.0});
    EddyProto.CRDTEddy state2 = buildEddy(eddyId, "replica-2", 200L, new double[]{0.0, 2.0, 0.0});
    EddyProto.CRDTEddy state3 = buildEddy(eddyId, "replica-3", 150L, new double[]{0.0, 0.0, 3.0});

    r1.putState(eddyId, state1);
    r2.putState(eddyId, state2);
    r3.putState(eddyId, state3);

    System.out.println("   R1: ts=" + state1.getState().getTimestamp() + " replica=" + state1.getState().getReplicaId());
    System.out.println("   R2: ts=" + state2.getState().getTimestamp() + " replica=" + state2.getState().getReplicaId());
    System.out.println("   R3: ts=" + state3.getState().getTimestamp() + " replica=" + state3.getState().getReplicaId());

    // Simulate gossip rounds
    System.out.println("\n1. Gossip round 1: R1 ↔ R2");
    gossipSync(r1, r2, eddyId);
    System.out.println("   R1: ts=" + r1.getState().get(eddyId).getState().getTimestamp());
    System.out.println("   R2: ts=" + r2.getState().get(eddyId).getState().getTimestamp());

    System.out.println("\n2. Gossip round 2: R2 ↔ R3");
    gossipSync(r2, r3, eddyId);
    System.out.println("   R2: ts=" + r2.getState().get(eddyId).getState().getTimestamp());
    System.out.println("   R3: ts=" + r3.getState().get(eddyId).getState().getTimestamp());

    System.out.println("\n3. Gossip round 3: R3 ↔ R1");
    gossipSync(r3, r1, eddyId);
    System.out.println("   R3: ts=" + r3.getState().get(eddyId).getState().getTimestamp());
    System.out.println("   R1: ts=" + r1.getState().get(eddyId).getState().getTimestamp());

    // Check convergence
    System.out.println("\n4. Final convergence check:");
    EddyProto.CRDTEddy final1 = r1.getState().get(eddyId);
    EddyProto.CRDTEddy final2 = r2.getState().get(eddyId);
    EddyProto.CRDTEddy final3 = r3.getState().get(eddyId);

    long ts1 = final1.getState().getTimestamp();
    long ts2 = final2.getState().getTimestamp();
    long ts3 = final3.getState().getTimestamp();

    System.out.println("   All replicas converged to ts=" + ts1 + ": " +
      (ts1 == ts2 && ts2 == ts3 && ts1 == 200L ? "✓ YES" : "✗ NO"));

    System.out.println("   All replicas have replica-2 state: " +
      (final1.getState().getReplicaId().equals("replica-2") &&
        final2.getState().getReplicaId().equals("replica-2") &&
        final3.getState().getReplicaId().equals("replica-2") ? "✓ YES" : "✗ NO"));

    // Show why r2 won (LWW)
    System.out.println("\n5. Why replica-2 state won (LWW semantics):");
    System.out.println("   R1 timestamp: 100 (oldest)");
    System.out.println("   R2 timestamp: 200 (HIGHEST) ← Winner");
    System.out.println("   R3 timestamp: 150");
    System.out.println("   → Highest timestamp wins, independent of message order");

    // Demonstrate idempotence
    System.out.println("\n6. Idempotence (merging again doesn't change state):");
    EddyProto.CRDTEddy beforeExtra = r1.getState().get(eddyId);
    gossipSync(r1, r2, eddyId); // Extra gossip round
    EddyProto.CRDTEddy afterExtra = r1.getState().get(eddyId);
    System.out.println("   Before extra merge: ts=" + beforeExtra.getState().getTimestamp());
    System.out.println("   After extra merge: ts=" + afterExtra.getState().getTimestamp());
    System.out.println("   Unchanged: " + (beforeExtra.getState().getTimestamp() ==
      afterExtra.getState().getTimestamp() ? "✓ YES" : "✗ NO"));

    System.out.println("\n=== Example complete ===");
  }

  /**
   * Simulate gossip sync: both replicas merge each other's state.
   */
  private static void gossipSync(Replica sender, Replica receiver, String eddyId) {
    EddyProto.CRDTEddy senderState = sender.getState().get(eddyId);

    // Receiver merges sender's state
    receiver.merge(eddyId, senderState);

    // Sender merges receiver's updated state
    sender.merge(eddyId, receiver.getState().get(eddyId));
  }

  /**
   * Build a test CRDTEddy.
   */
  private static EddyProto.CRDTEddy buildEddy(String eddyId, String replica, long timestamp,
    double[] vector) {
    EddyProto.CRDTVector state = EddyProto.CRDTVector.newBuilder()
      .setTimestamp(timestamp)
      .setReplicaId(replica)
      .addAllVector(convertToList(vector))
      .build();

    return EddyProto.CRDTEddy.newBuilder()
      .setId(eddyId)
      .setState(state)
      .build();
  }

  private static List<Double> convertToList(double[] arr) {
    List<Double> list = new ArrayList<>();
    for (double v : arr) {
      list.add(v);
    }
    return list;
  }
}
