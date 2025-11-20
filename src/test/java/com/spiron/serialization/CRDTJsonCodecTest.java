package com.spiron.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spiron.proto.EddyProto.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CRDT JSON codec.
 *
 * Validates serialization/deserialization of proto messages to/from JSON.
 */
public class CRDTJsonCodecTest {

  private CRDTJsonCodec codec;
  private CRDTEddy testEddy;

  @BeforeEach
  void setup() {
    codec = new CRDTJsonCodec();

    CRDTVector vector = CRDTVector.newBuilder()
      .addAllVector(Arrays.asList(1.0, 2.0, 3.0))
      .setTimestamp(12345L)
      .setReplicaId("node-1")
      .build();

    ApprovalCounter approvals = ApprovalCounter.newBuilder()
      .putPerReplica("node-1", 5)
      .putPerReplica("node-2", 3)
      .build();

    testEddy = CRDTEddy.newBuilder()
      .setId("eddy-test")
      .setState(vector)
      .setApprovals(approvals)
      .setLastUpdated(System.currentTimeMillis())
      .build();
  }

  /**
   * Test Eddy serialization to JSON
   */
  @Test
  void testEddySerializationToJson() {
    String json = codec.serializeEddy(testEddy);

    assertThat(json).isNotNull().isNotEmpty();
    assertThat(json).contains("eddy-test").contains("node-1").contains("1.0");
  }

  /**
   * Test Eddy deserialization from JSON
   */
  @Test
  void testEddyDeserialization() {
    String json = codec.serializeEddy(testEddy);
    CRDTEddy deserialized = codec.deserializeEddy(json);

    assertThat(deserialized).isNotNull();
    assertThat(deserialized.getId()).isEqualTo("eddy-test");
    assertThat(deserialized.getState().getReplicaId()).isEqualTo("node-1");
    assertThat(deserialized.getApprovals().getPerReplicaMap()).containsEntry(
      "node-1",
      5L
    );
  }

  /**
   * Test round-trip serialization (serialize -> deserialize -> serialize)
   */
  @Test
  void testEddyRoundTrip() {
    String json1 = codec.serializeEddy(testEddy);
    CRDTEddy deserialized = codec.deserializeEddy(json1);
    String json2 = codec.serializeEddy(deserialized);

    CRDTEddy deserialized2 = codec.deserializeEddy(json2);
    assertThat(deserialized2).isEqualTo(testEddy);
  }

  /**
   * Test CRDTVector serialization
   */
  @Test
  void testVectorSerialization() {
    CRDTVector vector = testEddy.getState();
    String json = codec.serializeVector(vector);

    assertThat(json).isNotNull().isNotEmpty();
    assertThat(json).contains("node-1").contains("12345");
  }

  /**
   * Test CRDTVector deserialization
   */
  @Test
  void testVectorDeserialization() {
    CRDTVector vector = testEddy.getState();
    String json = codec.serializeVector(vector);
    CRDTVector deserialized = codec.deserializeVector(json);

    assertThat(deserialized.getReplicaId()).isEqualTo("node-1");
    assertThat(deserialized.getTimestamp()).isEqualTo(12345L);
    assertThat(deserialized.getVectorList()).containsExactly(1.0, 2.0, 3.0);
  }

  /**
   * Test ApprovalCounter serialization
   */
  @Test
  void testApprovalCounterSerialization() {
    ApprovalCounter counter = testEddy.getApprovals();
    String json = codec.serializeApprovals(counter);

    assertThat(json).isNotNull().isNotEmpty();
    assertThat(json).contains("node-1").contains("node-2");
  }

  /**
   * Test ApprovalCounter deserialization
   */
  @Test
  void testApprovalCounterDeserialization() {
    ApprovalCounter counter = testEddy.getApprovals();
    String json = codec.serializeApprovals(counter);
    ApprovalCounter deserialized = codec.deserializeApprovals(json);

    assertThat(deserialized.getPerReplicaOrDefault("node-1", 0L)).isEqualTo(5);
    assertThat(deserialized.getPerReplicaOrDefault("node-2", 0L)).isEqualTo(3);
  }

  /**
   * Test null serialization
   */
  @Test
  void testNullSerializationEddy() {
    String json = codec.serializeEddy(null);
    assertThat(json).isNull();
  }

  /**
   * Test null deserialization
   */
  @Test
  void testNullDeserializationEddy() {
    CRDTEddy eddy = codec.deserializeEddy(null);
    assertThat(eddy).isNull();
  }

  /**
   * Test empty string deserialization
   */
  @Test
  void testEmptyStringDeserialization() {
    CRDTEddy eddy = codec.deserializeEddy("");
    assertThat(eddy).isNull();
  }

  /**
   * Test invalid JSON deserialization throws exception
   */
  @Test
  void testInvalidJsonThrowsException() {
    assertThatThrownBy(() -> codec.deserializeEddy("{invalid json"))
      .isInstanceOf(RuntimeException.class)
      .hasMessageContaining("Deserialization failed");
  }

  /**
   * Test multiple Eddies serialization/deserialization
   */
  @Test
  void testMultipleEddies() {
    CRDTEddy eddy1 = testEddy;
    CRDTEddy eddy2 = CRDTEddy.newBuilder()
      .setId("eddy-2")
      .setState(
        CRDTVector.newBuilder()
          .setTimestamp(200L)
          .setReplicaId("node-2")
          .addVector(4.0)
          .build()
      )
      .setApprovals(
        ApprovalCounter.newBuilder().putPerReplica("node-3", 7).build()
      )
      .build();

    String json1 = codec.serializeEddy(eddy1);
    String json2 = codec.serializeEddy(eddy2);

    CRDTEddy deser1 = codec.deserializeEddy(json1);
    CRDTEddy deser2 = codec.deserializeEddy(json2);

    assertThat(deser1.getId()).isEqualTo("eddy-test");
    assertThat(deser2.getId()).isEqualTo("eddy-2");
  }
}
