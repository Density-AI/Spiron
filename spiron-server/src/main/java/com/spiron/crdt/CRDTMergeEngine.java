package com.spiron.crdt;

import com.spiron.proto.EddyProto;
import com.spiron.metrics.EnergyMetrics;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CRDT merge engine: Implements deterministic, commutative, associative merging of CRDT Eddy states.
 *
 * Merge semantics:
 * 1. CRDTVector: Last-Writer-Wins (LWW) — select vector with highest timestamp.
 *    Tiebreaker: lexicographic order of replicaId (for determinism).
 * 2. ApprovalCounter: G-Counter merge — element-wise max per replica.
 *
 * Properties:
 * - Commutative: merge(a, b) == merge(b, a)
 * - Associative: merge(a, merge(b, c)) == merge(merge(a, b), c)
 * - Idempotent: merge(a, a) == a (no side effects from re-merging)
 * - Convergent: All replicas eventually converge to the same state after gossip.
 */
public class CRDTMergeEngine {

  private static final Logger log = LoggerFactory.getLogger(
    CRDTMergeEngine.class
  );
  
  private static EnergyMetrics metrics = null;
  
  /**
   * Set the metrics instance for instrumentation.
   */
  public static void setMetrics(EnergyMetrics energyMetrics) {
    metrics = energyMetrics;
  }

  private CRDTMergeEngine() {
    // Utility class
  }

  /**
   * Merge two CRDT Eddy states, producing the merged result.
   *
   * @param eddy1 first CRDT eddy (immutable)
   * @param eddy2 second CRDT eddy (immutable)
   * @return merged CRDT eddy with LWW vector and merged approval counter
   */
  public static EddyProto.CRDTEddy merge(
    EddyProto.CRDTEddy eddy1,
    EddyProto.CRDTEddy eddy2
  ) {
    if (eddy1 == null) return eddy2;
    if (eddy2 == null) return eddy1;
    if (eddy1.equals(eddy2)) return eddy1; // Idempotent: merging identical eddies returns same

    // Track merge operation
    if (metrics != null) {
      metrics.incMerge();
    }

    // Merge vectors using LWW rule
    EddyProto.CRDTVector mergedVector = mergeLWWVector(
      eddy1.getState(),
      eddy2.getState()
    );

    // Merge approval counters using G-Counter merge
    EddyProto.ApprovalCounter mergedApprovals = mergeApprovals(
      eddy1.getApprovals(),
      eddy2.getApprovals()
    );

    // Update lastUpdated to the most recent timestamp
    long lastUpdated = Math.max(eddy1.getLastUpdated(), eddy2.getLastUpdated());

    log.debug(
      "Merged eddies: id={}, newVector={}@{}, approvalsCumulative={}",
      eddy1.getId(),
      mergedVector.getReplicaId(),
      mergedVector.getTimestamp(),
      getCumulativeApprovals(mergedApprovals)
    );

    return EddyProto.CRDTEddy.newBuilder()
      .setId(eddy1.getId())
      .setState(mergedVector)
      .setApprovals(mergedApprovals)
      .setLastUpdated(lastUpdated)
      .build();
  }

  /**
   * Merge two CRDTVectors using Last-Writer-Wins (LWW) semantics:
   * - Select the vector with the highest timestamp.
   * - If timestamps are equal, use lexicographic comparison of replicaId for determinism.
   *
   * @param vec1 first vector
   * @param vec2 second vector
   * @return the "winner" vector (LWW)
   */
  private static EddyProto.CRDTVector mergeLWWVector(
    EddyProto.CRDTVector vec1,
    EddyProto.CRDTVector vec2
  ) {
    if (vec1 == null) return vec2;
    if (vec2 == null) return vec1;

    long ts1 = vec1.getTimestamp();
    long ts2 = vec2.getTimestamp();

    if (ts1 > ts2) {
      return vec1;
    } else if (ts2 > ts1) {
      return vec2;
    } else {
      // Timestamps equal: use replicaId lexicographic comparison for determinism
      String rep1 = vec1.getReplicaId();
      String rep2 = vec2.getReplicaId();
      int cmp = rep1.compareTo(rep2);
      if (cmp >= 0) {
        return vec1;
      } else {
        return vec2;
      }
    }
  }

  /**
   * Merge two ApprovalCounter proto messages using G-Counter merge (element-wise max).
   * Returns a merged ApprovalCounter proto.
   *
   * @param appr1 first proto approval counter
   * @param appr2 second proto approval counter
   * @return merged proto approval counter
   */
  private static EddyProto.ApprovalCounter mergeApprovals(
    EddyProto.ApprovalCounter appr1,
    EddyProto.ApprovalCounter appr2
  ) {
    EddyProto.ApprovalCounter.Builder builder =
      EddyProto.ApprovalCounter.newBuilder();

    // Merge from first counter
    if (appr1 != null) {
      Map<String, Long> map1 = appr1.getPerReplicaMap();
      for (String replicaId : map1.keySet()) {
        builder.putPerReplica(replicaId, map1.get(replicaId));
      }
    }

    // Merge from second counter (element-wise max)
    if (appr2 != null) {
      Map<String, Long> map2 = appr2.getPerReplicaMap();
      for (String replicaId : map2.keySet()) {
        long val2 = map2.get(replicaId);
        long val1 = appr1 != null
          ? appr1.getPerReplicaOrDefault(replicaId, 0L)
          : 0L;
        builder.putPerReplica(replicaId, Math.max(val1, val2));
      }
    }

    return builder.build();
  }

  /**
   * Get cumulative approval count from a proto ApprovalCounter.
   * @param counter the approval counter
   * @return sum of all per-replica counts
   */
  public static long getCumulativeApprovals(EddyProto.ApprovalCounter counter) {
    if (counter == null) return 0L;
    long sum = 0;
    Map<String, Long> perReplicaMap = counter.getPerReplicaMap();
    for (long val : perReplicaMap.values()) {
      sum += val;
    }
    return sum;
  }
}
