package com.spiron.crdt;

import com.spiron.proto.EddyProto;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finality Detector: Monitors cumulative approval thresholds for Eddy states.
 *
 * Semantics:
 * - Eddy reaches finality when cumulative approvals (sum of per-replica counts) >= threshold.
 * - Finality is deterministic and requires no leader or voting: all replicas independently
 *   compute the same result based on the approval counter state.
 * - Once finality is reached for an Eddy, it is immutable (byzantine-safe).
 *
 * Usage:
 * detector.checkFinality(eddyId, eddy, threshold) -> Optional<Long> cumulative approvals
 * If present, finality is reached; absent means not yet final.
 */
public class FinallityDetector {

  private static final Logger log = LoggerFactory.getLogger(
    FinallityDetector.class
  );

  // Track final eddies: once an Eddy reaches finality, it cannot change
  // Assumption: Eddy IDs are globally unique and immutable.
  private final Map<String, EddyProto.CRDTEddy> finalizedEddies =
    new ConcurrentHashMap<>();

  public FinallityDetector() {}

  /**
   * Check if an Eddy has reached finality based on cumulative approvals.
   *
   * @param eddyId the eddy identifier
   * @param eddy the CRDT eddy state
   * @param threshold minimum cumulative approvals required for finality
   * @return Optional containing cumulative approvals if finality reached, empty otherwise
   */
  public Optional<Long> checkFinality(
    String eddyId,
    EddyProto.CRDTEddy eddy,
    long threshold
  ) {
    // Check if already finalized
    if (finalizedEddies.containsKey(eddyId)) {
      EddyProto.CRDTEddy final_eddy = finalizedEddies.get(eddyId);
      long cumulative = CRDTMergeEngine.getCumulativeApprovals(
        final_eddy.getApprovals()
      );
      return Optional.of(cumulative);
    }

    // Calculate cumulative approvals
    long cumulative = CRDTMergeEngine.getCumulativeApprovals(
      eddy.getApprovals()
    );

    // Check if threshold met
    if (cumulative >= threshold) {
      finalizedEddies.put(eddyId, eddy);
      log.info(
        "Eddy {} reached finality: cumulative approvals={} >= threshold={}",
        eddyId,
        cumulative,
        threshold
      );
      return Optional.of(cumulative);
    }

    return Optional.empty();
  }

  /**
   * Check if an Eddy has been finalized.
   *
   * @param eddyId the eddy identifier
   * @return true if finality has been reached
   */
  public boolean isFinalized(String eddyId) {
    return finalizedEddies.containsKey(eddyId);
  }

  /**
   * Get the finalized Eddy, if it exists.
   *
   * @param eddyId the eddy identifier
   * @return the finalized Eddy state, or empty
   */
  public Optional<EddyProto.CRDTEddy> getFinalizedEddy(String eddyId) {
    return Optional.ofNullable(finalizedEddies.get(eddyId));
  }

  /**
   * Get all finalized eddies.
   *
   * @return snapshot of finalized eddies
   */
  public Map<String, EddyProto.CRDTEddy> getFinalizedEddies() {
    return new HashMap<>(finalizedEddies);
  }

  /**
   * Clear finalized state (for testing only).
   */
  public void clear() {
    finalizedEddies.clear();
  }

  /**
   * Get the count of finalized eddies.
   */
  public int getFinalizedCount() {
    return finalizedEddies.size();
  }
}
