package com.spiron.examples;

import com.spiron.crdt.ApprovalCounter;
import com.spiron.crdt.CRDTMergeEngine;
import com.spiron.proto.EddyProto;
import com.spiron.serialization.CRDTJsonCodec;

/**
 * Example: CRDT Basics
 *
 * Demonstrates core CRDT operations:
 * - Creating CRDT vector states with LWW (Last-Writer-Wins) semantics
 * - Merging concurrent updates
 * - Tracking approvals with G-Counter
 * - Serializing to/from JSON for persistence
 *
 * Key Concepts:
 * - CRDTVector: LWW register with timestamp-based conflict resolution
 * - ApprovalCounter: Non-decreasing counter (per-replica) for tracking finality
 * - Merge: Commutative, associative, idempotent operation → Eventual Consistency
 *
 * Run: java com.spiron.examples.CRDTBasicsExample
 */
public class CRDTBasicsExample {

  public static void main(String[] args) {
    System.out.println("=== CRDT Basics Example ===\n");

    CRDTJsonCodec codec = new CRDTJsonCodec();

    // Example 1: Create two concurrent vector updates with different timestamps
    System.out.println("1. Creating concurrent CRDT vectors:");
    EddyProto.CRDTVector vec1 = buildVector("replica-1", 1000L, new double[]{10.0, 20.0, 30.0});
    EddyProto.CRDTVector vec2 = buildVector("replica-2", 2000L, new double[]{5.0, 15.0, 25.0});
    System.out.println("   Vector 1 (ts=1000): " + vec1.getVectorList());
    System.out.println("   Vector 2 (ts=2000): " + vec2.getVectorList());

    // Example 2: Create full CRDT Eddy states
    System.out.println("\n2. Creating CRDT Eddy states:");
    EddyProto.CRDTEddy eddy1 = EddyProto.CRDTEddy.newBuilder()
      .setId("state-1")
      .setState(vec1)
      .build();
    EddyProto.CRDTEddy eddy2 = EddyProto.CRDTEddy.newBuilder()
      .setId("state-1")
      .setState(vec2)
      .build();
    System.out.println("   Eddy 1: " + eddy1.getId() + " (ts=" + eddy1.getState().getTimestamp() + ")");
    System.out.println("   Eddy 2: " + eddy2.getId() + " (ts=" + eddy2.getState().getTimestamp() + ")");

    // Example 3: Merge the two states - LWW selects vec2 (higher timestamp)
    System.out.println("\n3. Merging concurrent states (CRDT merge):");
    EddyProto.CRDTEddy merged = CRDTMergeEngine.merge(eddy1, eddy2);
    System.out.println("   Merged state vector: " + merged.getState().getVectorList());
    System.out.println("   Winner timestamp: " + merged.getState().getTimestamp());
    System.out.println("   Winner replica: " + merged.getState().getReplicaId());

    // Example 4: Demonstrate idempotence (merge same state again)
    System.out.println("\n4. Merge idempotence (merging same state twice):");
    EddyProto.CRDTEddy merged2 = CRDTMergeEngine.merge(merged, eddy1);
    System.out.println("   Merged again: " + merged2.getState().getVectorList());
    System.out.println("   Same as before: " + merged.getState().getVectorList().equals(
      merged2.getState().getVectorList()));

    // Example 5: Add approvals and demonstrate G-Counter merge
    System.out.println("\n5. Approval tracking (G-Counter):");
    EddyProto.ApprovalCounter approvals1 = buildApprovals(
      "replica-1", 2,
      "replica-2", 1
    );
    EddyProto.ApprovalCounter approvals2 = buildApprovals(
      "replica-1", 2,
      "replica-2", 3
    );
    System.out.println("   Approvals 1: " + approvals1.getPerReplicaMap());
    System.out.println("   Approvals 2: " + approvals2.getPerReplicaMap());

    // Merge approvals (element-wise max)
    EddyProto.ApprovalCounter mergedApprovals = mergeApprovals(approvals1, approvals2);
    System.out.println("   Merged approvals (max per replica): " + mergedApprovals.getPerReplicaMap());

    // Example 6: JSON serialization
    System.out.println("\n6. JSON serialization for persistence:");
    String eddyJson = codec.serializeEddy(merged);
    System.out.println("   Eddy as JSON (first 100 chars): " +
      eddyJson.substring(0, Math.min(100, eddyJson.length())) + "...");

    // Deserialize back
    EddyProto.CRDTEddy deserialized = codec.deserializeEddy(eddyJson);
    System.out.println("   Deserialized back: " + deserialized.getId() +
      " (ts=" + deserialized.getState().getTimestamp() + ")");

    // Example 7: Demonstrate commutativity
    System.out.println("\n7. Merge commutativity (order doesn't matter):");
    EddyProto.CRDTEddy merge_1_2 = CRDTMergeEngine.merge(eddy1, eddy2);
    EddyProto.CRDTEddy merge_2_1 = CRDTMergeEngine.merge(eddy2, eddy1);
    System.out.println("   Merge(eddy1, eddy2): ts=" + merge_1_2.getState().getTimestamp());
    System.out.println("   Merge(eddy2, eddy1): ts=" + merge_2_1.getState().getTimestamp());
    System.out.println("   Same result: " + (merge_1_2.getState().getTimestamp() ==
      merge_2_1.getState().getTimestamp()));

    System.out.println("\n=== Example complete ===");
  }

  /**
   * Build a CRDTVector with given parameters.
   */
  private static EddyProto.CRDTVector buildVector(String replicaId, long timestamp, double[] values) {
    EddyProto.CRDTVector.Builder builder = EddyProto.CRDTVector.newBuilder();
    builder.setReplicaId(replicaId);
    builder.setTimestamp(timestamp);
    for (double v : values) {
      builder.addVector(v);
    }
    return builder.build();
  }

  /**
   * Build an ApprovalCounter with given replica approval counts.
   */
  private static EddyProto.ApprovalCounter buildApprovals(Object... replicaCounts) {
    EddyProto.ApprovalCounter.Builder builder = EddyProto.ApprovalCounter.newBuilder();
    for (int i = 0; i < replicaCounts.length; i += 2) {
      String replica = (String) replicaCounts[i];
      long count = (long) replicaCounts[i + 1];
      builder.putPerReplica(replica, count);
    }
    return builder.build();
  }

  /**
   * Merge two ApprovalCounters (element-wise max).
   */
  private static EddyProto.ApprovalCounter mergeApprovals(
    EddyProto.ApprovalCounter a1,
    EddyProto.ApprovalCounter a2
  ) {
    EddyProto.ApprovalCounter.Builder builder = EddyProto.ApprovalCounter.newBuilder();
    
    // Add all keys from a1
    for (String replica : a1.getPerReplicaMap().keySet()) {
      long v1 = a1.getPerReplicaMap().getOrDefault(replica, 0L);
      long v2 = a2.getPerReplicaMap().getOrDefault(replica, 0L);
      builder.putPerReplica(replica, Math.max(v1, v2));
    }
    
    // Add any keys only in a2
    for (String replica : a2.getPerReplicaMap().keySet()) {
      if (!a1.getPerReplicaMap().containsKey(replica)) {
        builder.putPerReplica(replica, a2.getPerReplicaMap().get(replica));
      }
    }
    
    return builder.build();
  }
}
