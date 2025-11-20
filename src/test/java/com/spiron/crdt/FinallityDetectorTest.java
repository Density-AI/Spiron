package com.spiron.crdt;

import static org.assertj.core.api.Assertions.assertThat;

import com.spiron.proto.EddyProto.ApprovalCounter;
import com.spiron.proto.EddyProto.CRDTEddy;
import com.spiron.proto.EddyProto.CRDTVector;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Finality Detector.
 *
 * Validates:
 * 1. Finality is reached when cumulative approvals >= threshold
 * 2. Once final, eddy cannot change (immutability)
 * 3. Finality is deterministic (independent of node order)
 */
public class FinallityDetectorTest {

  private FinallityDetector detector;
  private CRDTEddy eddyBelowThreshold;
  private CRDTEddy eddyAboveThreshold;

  @BeforeEach
  void setup() {
    detector = new FinallityDetector();

    // Eddy with cumulative approvals < 10 (below threshold)
    eddyBelowThreshold = CRDTEddy.newBuilder()
      .setId("eddy-below")
      .setState(
        CRDTVector.newBuilder()
          .setTimestamp(100)
          .setReplicaId("node-1")
          .addVector(1.0)
          .build()
      )
      .setApprovals(
        ApprovalCounter.newBuilder()
          .putPerReplica("node-1", 3)
          .putPerReplica("node-2", 2)
          .build()
      )
      .setLastUpdated(System.currentTimeMillis())
      .build();

    // Eddy with cumulative approvals >= 10 (above threshold)
    eddyAboveThreshold = CRDTEddy.newBuilder()
      .setId("eddy-above")
      .setState(
        CRDTVector.newBuilder()
          .setTimestamp(200)
          .setReplicaId("node-1")
          .addVector(2.0)
          .build()
      )
      .setApprovals(
        ApprovalCounter.newBuilder()
          .putPerReplica("node-1", 4)
          .putPerReplica("node-2", 3)
          .putPerReplica("node-3", 3)
          .build()
      )
      .setLastUpdated(System.currentTimeMillis())
      .build();
  }

  /**
   * Test finality detection: eddy below threshold returns empty
   */
  @Test
  void testFinalityBelowThreshold() {
    long threshold = 10;
    Optional<Long> finality = detector.checkFinality(
      "eddy-below",
      eddyBelowThreshold,
      threshold
    );

    assertThat(finality).isEmpty();
    assertThat(detector.isFinalized("eddy-below")).isFalse();
  }

  /**
   * Test finality detection: eddy above threshold returns cumulative
   */
  @Test
  void testFinalityAboveThreshold() {
    long threshold = 10;
    Optional<Long> finality = detector.checkFinality(
      "eddy-above",
      eddyAboveThreshold,
      threshold
    );

    assertThat(finality).isPresent();
    assertThat(finality.get()).isEqualTo(10); // 4 + 3 + 3
    assertThat(detector.isFinalized("eddy-above")).isTrue();
  }

  /**
   * Test immutability: once final, subsequent checks return same state
   */
  @Test
  void testFinalityImmutability() {
    long threshold = 10;

    // First check should reach finality
    Optional<Long> finality1 = detector.checkFinality(
      "eddy-above",
      eddyAboveThreshold,
      threshold
    );
    assertThat(finality1).isPresent().contains(10L);

    // Second check should return same state
    Optional<Long> finality2 = detector.checkFinality(
      "eddy-above",
      eddyAboveThreshold,
      threshold
    );
    assertThat(finality2).isPresent().contains(10L);
  }

  /**
   * Test getFinalizedEddy
   */
  @Test
  void testGetFinalizedEddy() {
    long threshold = 10;
    detector.checkFinality("eddy-above", eddyAboveThreshold, threshold);

    Optional<CRDTEddy> finalEddy = detector.getFinalizedEddy("eddy-above");
    assertThat(finalEddy).isPresent();
    assertThat(finalEddy.get().getId()).isEqualTo("eddy-above");
  }

  /**
   * Test getFinalizedEddies
   */
  @Test
  void testGetFinalizedEddies() {
    long threshold = 10;
    detector.checkFinality("eddy-1", createEddy("eddy-1", 10), threshold);
    detector.checkFinality("eddy-2", createEddy("eddy-2", 15), threshold);

    Map<String, CRDTEddy> finalized = detector.getFinalizedEddies();
    assertThat(finalized).hasSize(2).containsKeys("eddy-1", "eddy-2");
  }

  /**
   * Test getFinalizedCount
   */
  @Test
  void testGetFinalizedCount() {
    long threshold = 10;
    detector.checkFinality("eddy-1", createEddy("eddy-1", 10), threshold);
    detector.checkFinality("eddy-2", createEddy("eddy-2", 15), threshold);
    detector.checkFinality("eddy-3", createEddy("eddy-3", 5), threshold); // Does not finalize

    assertThat(detector.getFinalizedCount()).isEqualTo(2);
  }

  /**
   * Test clear
   */
  @Test
  void testClear() {
    long threshold = 10;
    detector.checkFinality("eddy-1", createEddy("eddy-1", 10), threshold);
    assertThat(detector.getFinalizedCount()).isEqualTo(1);

    detector.clear();
    assertThat(detector.getFinalizedCount()).isEqualTo(0);
    assertThat(detector.isFinalized("eddy-1")).isFalse();
  }

  /**
   * Test determinism: same eddy always produces same finality result
   */
  @Test
  void testDeterminism() {
    long threshold = 10;

    Optional<Long> finality1 = detector.checkFinality(
      "eddy",
      eddyAboveThreshold,
      threshold
    );
    // Clear and retest
    detector.clear();
    Optional<Long> finality2 = detector.checkFinality(
      "eddy",
      eddyAboveThreshold,
      threshold
    );

    assertThat(finality1).isEqualTo(finality2);
  }

  // Helper: create eddy with given cumulative approvals
  private CRDTEddy createEddy(String id, long cumulativeApprovals) {
    return CRDTEddy.newBuilder()
      .setId(id)
      .setState(
        CRDTVector.newBuilder()
          .setTimestamp(System.currentTimeMillis())
          .setReplicaId("node-1")
          .addVector(1.0)
          .build()
      )
      .setApprovals(
        ApprovalCounter.newBuilder()
          .putPerReplica("node-1", cumulativeApprovals)
          .build()
      )
      .setLastUpdated(System.currentTimeMillis())
      .build();
  }
}
