package com.spiron.storage;

import java.util.Map;
import java.util.Optional;

/**
 * Abstraction for persistent CRDT state storage.
 * Implementations must provide durable persistence (no in-memory-only state).
 * Supports RocksDB (solo mode) and etcd (cluster mode).
 *
 * Note: Uses JSON serialization of CRDT state; specific message types
 * will be defined after proto regeneration.
 */
public interface CRDTStore {
  /** Store or update a CRDT Eddy. Persists immediately. */
  void put(String eddyId, String eddyJsonState);

  /** Retrieve a CRDT Eddy by id (as JSON), or empty if not found. */
  Optional<String> get(String eddyId);

  /** Get all stored eddies as Map (eddyId -> JSON state snapshot). */
  Map<String, String> getAll();

  /** Delete an eddy entry. */
  void delete(String eddyId);

  /** Clear all stored data (careful!). */
  void clear();

  /** Check if eddy exists. */
  boolean exists(String eddyId);

  /** Store lineage (ancestry chain) for an eddy. Persists immediately. */
  void putLineage(String eddyId, String lineageJson);

  /** Retrieve lineage for an eddy by id (as JSON), or empty if not found. */
  Optional<String> getLineage(String eddyId);

  /** Close and release resources. */
  void close();
}
