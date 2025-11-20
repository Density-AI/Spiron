package com.spiron.crdt;

import com.spiron.metrics.EnergyMetrics;
import java.util.*;

/**
 * G-Counter CRDT: A grow-only counter that supports:
 *  - increment() on local replica
 *  - merge() for combining states from multiple replicas
 *
 * Semantics: All increments are commutative, associative, idempotent.
 * merge(a, merge(b, c)) == merge(merge(a, b), c) == merge(a, b, c)
 *
 * Implementation: Each replica maintains its own non-decreasing counter.
 * Global value = sum of all per-replica counters.
 * Merge via element-wise max per replica.
 */
public class ApprovalCounter {

  private final Map<String, Long> perReplicaCount;
  private static EnergyMetrics metrics = null;
  
  /**
   * Set the metrics instance for instrumentation.
   */
  public static void setMetrics(EnergyMetrics energyMetrics) {
    metrics = energyMetrics;
  }

  public ApprovalCounter() {
    this.perReplicaCount = new HashMap<>();
  }

  public ApprovalCounter(Map<String, Long> perReplicaCount) {
    this.perReplicaCount = new HashMap<>(perReplicaCount);
  }

  /**
   * Increment the counter for a given replica (replica must be the caller's own replicaId).
   */
  public void increment(String replicaId) {
    perReplicaCount.merge(replicaId, 1L, Long::sum);
    if (metrics != null) {
      metrics.incCrdtIngest();
    }
  }

  /**
   * Get the per-replica counter value.
   */
  public long get(String replicaId) {
    return perReplicaCount.getOrDefault(replicaId, 0L);
  }

  /**
   * Get the cumulative value (sum of all per-replica counts).
   * This is used to determine finality: when cumulative approvals >= threshold, consensus is reached.
   */
  public long getCumulative() {
    return perReplicaCount.values().stream().mapToLong(Long::longValue).sum();
  }

  /**
   * Get a snapshot of the entire per-replica map.
   */
  public Map<String, Long> getPerReplicaMap() {
    return new HashMap<>(perReplicaCount);
  }

  /**
   * Merge another ApprovalCounter into this one using the G-Counter merge rule:
   * For each replica, take the maximum counter value.
   *
   * This operation is:
   * - Commutative: merge(a, b) == merge(b, a)
   * - Associative: merge(a, merge(b, c)) == merge(merge(a, b), c)
   * - Idempotent: merge(a, a) == a
   * - Monotonic: cumulative never decreases after merge
   */
  public void merge(ApprovalCounter other) {
    if (other == null) return;
    Map<String, Long> otherMap = other.getPerReplicaMap();
    for (String replicaId : otherMap.keySet()) {
      long otherVal = otherMap.get(replicaId);
      perReplicaCount.merge(replicaId, otherVal, Math::max);
    }
    // Also retain replicas that are in our map but not in other (no merging needed)
  }

  /**
   * Create a copy of this counter.
   */
  public ApprovalCounter copy() {
    return new ApprovalCounter(new HashMap<>(perReplicaCount));
  }

  /**
   * Check if this counter has any approvals.
   */
  public boolean isEmpty() {
    return perReplicaCount.isEmpty() || getCumulative() == 0;
  }

  @Override
  public String toString() {
    return (
      "ApprovalCounter{" +
      "perReplica=" +
      perReplicaCount +
      ", cumulative=" +
      getCumulative() +
      '}'
    );
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ApprovalCounter that = (ApprovalCounter) o;
    return Objects.equals(perReplicaCount, that.perReplicaCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(perReplicaCount);
  }
}
