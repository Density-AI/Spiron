package com.spiron.core;

import java.io.Serializable;

/**
 * Represents the state of an Eddy in the Spiron consensus system.
 * An Eddy is a fundamental unit of consensus carrying a multi-dimensional vector
 * and energy value that determines its dominance in the consensus process.
 *
 * <p>The vector represents the consensus value (normalized to unit length),
 * while energy represents the strength or confidence of this state.</p>
 *
 * <p>This record is immutable and serializable for network transmission and persistence.</p>
 *
 * <p><b>Lineage is NOT stored in-memory:</b> The ancestry list is persisted to RocksDB/etcd
 * and retrieved asynchronously when needed. This EddyState only carries the immediate parentId
 * for merge tracking, not the full ancestry chain.</p>
 *
 * @param id Unique identifier for this Eddy
 * @param vector Multi-dimensional vector (128-2000 dimensions) representing the consensus value
 * @param energy Current energy level of this Eddy, determines dominance in consensus
 * @param parentId The immediate parent Eddy's id (for lineage tracking), or null if root
 */
public record EddyState(String id, double[] vector, double energy, String parentId)
  implements Serializable {
  private static final long serialVersionUID = 1L;
}
