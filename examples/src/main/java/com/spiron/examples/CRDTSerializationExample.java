package com.spiron.examples;

import com.spiron.proto.EddyProto;
import com.spiron.serialization.CRDTJsonCodec;

/**
 * Example: JSON Serialization for CRDT State
 *
 * Demonstrates JSON codec usage for:
 * - Serializing CRDT state to JSON for persistence/network transfer
 * - Deserializing JSON back to proto objects
 * - Round-trip validation (serialization is lossless)
 *
 * Use Cases:
 * - Store CRDT state in RocksDB/etcd as JSON
 * - Send CRDT state over gRPC/HTTP using JSON
 * - Human-readable logging and debugging
 * - Data migration and backups
 *
 * Run: java com.spiron.examples.CRDTSerializationExample
 */
public class CRDTSerializationExample {

  public static void main(String[] args) {
    System.out.println("=== CRDT JSON Serialization Example ===\n");

    CRDTJsonCodec codec = new CRDTJsonCodec();

    // Example 1: Serialize a simple CRDTVector
    System.out.println("1. Serialize CRDTVector to JSON:");
    EddyProto.CRDTVector vector = EddyProto.CRDTVector.newBuilder()
      .setTimestamp(1609459200000L)
      .setReplicaId("node-1")
      .addAllVector(java.util.Arrays.asList(10.0, 20.0, 30.0))
      .build();

    String vectorJson = codec.serializeVector(vector);
    System.out.println("   JSON: " + vectorJson);

    // Deserialize back
    EddyProto.CRDTVector deserializedVector = codec.deserializeVector(vectorJson);
    System.out.println("   Deserialized timestamp: " + deserializedVector.getTimestamp());
    System.out.println("   Deserialized replica: " + deserializedVector.getReplicaId());
    System.out.println("   Deserialized vector: " + deserializedVector.getVectorList());

    // Example 2: Serialize ApprovalCounter
    System.out.println("\n2. Serialize ApprovalCounter to JSON:");
    EddyProto.ApprovalCounter approvals = EddyProto.ApprovalCounter.newBuilder()
      .putPerReplica("node-1", 3)
      .putPerReplica("node-2", 2)
      .putPerReplica("node-3", 5)
      .build();

    String approvalsJson = codec.serializeApprovals(approvals);
    System.out.println("   JSON: " + approvalsJson);

    // Deserialize and verify
    EddyProto.ApprovalCounter deserializedApprovals = codec.deserializeApprovals(approvalsJson);
    System.out.println("   Approvals after deserialization: " + deserializedApprovals.getPerReplicaMap());

    // Example 3: Serialize full CRDTEddy
    System.out.println("\n3. Serialize full CRDTEddy to JSON:");
    EddyProto.CRDTEddy eddy = EddyProto.CRDTEddy.newBuilder()
      .setId("eddy-transaction-1")
      .setState(vector)
      .setApprovals(approvals)
      .setLastUpdated(System.currentTimeMillis())
      .build();

    String eddyJson = codec.serializeEddy(eddy);
    System.out.println("   JSON length: " + eddyJson.length() + " chars");
    System.out.println("   JSON (formatted):\n" + prettyPrintJson(eddyJson));

    // Example 4: Round-trip validation
    System.out.println("\n4. Round-trip validation (serialize → deserialize → serialize):");
    EddyProto.CRDTEddy restored = codec.deserializeEddy(eddyJson);
    String eddyJson2 = codec.serializeEddy(restored);

    boolean roundTripValid = eddyJson.equals(eddyJson2);
    System.out.println("   First JSON length: " + eddyJson.length());
    System.out.println("   Second JSON length: " + eddyJson2.length());
    System.out.println("   Exact match (lossless): " + (roundTripValid ? "✓ YES" : "✗ NO"));

    // Example 5: Show JSON structure for understanding
    System.out.println("\n5. JSON Structure for persistence:");
    System.out.println("   Key fields in JSON:");
    System.out.println("   - \"id\": String (eddy identifier)");
    System.out.println("   - \"state\": CRDTVector (LWW vector with timestamp, replica, signature)");
    System.out.println("   - \"approvals\": ApprovalCounter (per-replica approval counts)");
    System.out.println("   - \"lastUpdated\": long (local merge timestamp)");

    // Example 6: Error handling
    System.out.println("\n6. Error handling:");
    try {
      String invalidJson = "{invalid json";
      codec.deserializeEddy(invalidJson);
      System.out.println("   ERROR: Should have thrown exception");
    } catch (RuntimeException e) {
      System.out.println("   ✓ Caught exception for invalid JSON: " + e.getClass().getSimpleName());
    }

    // Example 7: Null handling
    System.out.println("\n7. Null handling:");
    String nullResult = codec.serializeEddy(null);
    System.out.println("   Serialize null eddy: " + nullResult);
    EddyProto.CRDTEddy nullDeserialized = codec.deserializeEddy(null);
    System.out.println("   Deserialize null JSON: " + nullDeserialized);

    System.out.println("\n=== Example complete ===");
  }

  /**
   * Simple JSON pretty-printer (first 200 chars, truncated).
   */
  private static String prettyPrintJson(String json) {
    // Show just the first part since it's long
    String truncated = json.substring(0, Math.min(200, json.length()));
    if (json.length() > 200) {
      truncated += "... [" + json.length() + " total chars]";
    }
    return truncated;
  }
}
