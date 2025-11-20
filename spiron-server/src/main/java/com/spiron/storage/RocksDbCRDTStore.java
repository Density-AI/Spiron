package com.spiron.storage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.rocksdb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RocksDB-backed CRDT store for solo (single-node) mode.
 * Provides durability and fast key-value access using RocksDB.
 * Thread-safe implementation with proper resource management.
 */
public class RocksDbCRDTStore implements CRDTStore {

  private static final Logger log = LoggerFactory.getLogger(
    RocksDbCRDTStore.class
  );

  private static final String LINEAGE_PREFIX = "lineage:";

  static {
    RocksDB.loadLibrary();
  }

  private final Path dataDir;
  private final RocksDB db;
  private volatile boolean closed = false;

  public RocksDbCRDTStore(Path dataDir) {
    this.dataDir = dataDir;
    try {
      Files.createDirectories(dataDir);
      
      Options options = new Options()
        .setCreateIfMissing(true)
        .setCompressionType(CompressionType.LZ4_COMPRESSION)
        .setMaxBackgroundJobs(4);
      
      this.db = RocksDB.open(options, dataDir.toString());
      log.info("Initialized RocksDB CRDT store at {}", dataDir);
    } catch (Exception e) {
      throw new RuntimeException(
        "Failed to initialize RocksDB store at " + dataDir,
        e
      );
    }
  }

  @Override
  public void put(String eddyId, String eddyJsonState) {
    if (closed) throw new IllegalStateException("Store is closed");
    try {
      db.put(
        eddyId.getBytes(StandardCharsets.UTF_8),
        eddyJsonState.getBytes(StandardCharsets.UTF_8)
      );
      log.debug("Stored eddy {} in RocksDB", eddyId);
    } catch (RocksDBException e) {
      log.error("Failed to put eddy {} to RocksDB", eddyId, e);
      throw new RuntimeException("RocksDB write failed", e);
    }
  }

  @Override
  public Optional<String> get(String eddyId) {
    if (closed) throw new IllegalStateException("Store is closed");
    try {
      byte[] value = db.get(eddyId.getBytes(StandardCharsets.UTF_8));
      if (value == null) {
        return Optional.empty();
      }
      return Optional.of(new String(value, StandardCharsets.UTF_8));
    } catch (RocksDBException e) {
      log.error("Failed to get eddy {} from RocksDB", eddyId, e);
      return Optional.empty();
    }
  }

  @Override
  public Map<String, String> getAll() {
    if (closed) throw new IllegalStateException("Store is closed");
    Map<String, String> result = new HashMap<>();
    try (RocksIterator iterator = db.newIterator()) {
      for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
        String key = new String(iterator.key(), StandardCharsets.UTF_8);
        String value = new String(iterator.value(), StandardCharsets.UTF_8);
        result.put(key, value);
      }
    } catch (Exception e) {
      log.error("Failed to scan RocksDB", e);
    }
    return result;
  }

  @Override
  public void delete(String eddyId) {
    if (closed) throw new IllegalStateException("Store is closed");
    try {
      db.delete(eddyId.getBytes(StandardCharsets.UTF_8));
      log.debug("Deleted eddy {} from RocksDB", eddyId);
    } catch (RocksDBException e) {
      log.error("Failed to delete eddy {} from RocksDB", eddyId, e);
      throw new RuntimeException("RocksDB delete failed", e);
    }
  }

  @Override
  public void clear() {
    if (closed) throw new IllegalStateException("Store is closed");
    log.warn("Clearing all CRDT state from RocksDB");
    try (RocksIterator iterator = db.newIterator()) {
      List<byte[]> keysToDelete = new ArrayList<>();
      for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
        keysToDelete.add(iterator.key());
      }
      for (byte[] key : keysToDelete) {
        db.delete(key);
      }
      log.info("Cleared {} entries from RocksDB", keysToDelete.size());
    } catch (RocksDBException e) {
      log.error("Failed to clear RocksDB", e);
      throw new RuntimeException("RocksDB clear failed", e);
    }
  }

  @Override
  public boolean exists(String eddyId) {
    if (closed) throw new IllegalStateException("Store is closed");
    try {
      byte[] value = db.get(eddyId.getBytes(StandardCharsets.UTF_8));
      return value != null;
    } catch (RocksDBException e) {
      log.error("Failed to check existence of eddy {} in RocksDB", eddyId, e);
      return false;
    }
  }

  @Override
  public void putLineage(String eddyId, String lineageJson) {
    if (closed) throw new IllegalStateException("Store is closed");
    try {
      String key = LINEAGE_PREFIX + eddyId;
      db.put(
        key.getBytes(StandardCharsets.UTF_8),
        lineageJson.getBytes(StandardCharsets.UTF_8)
      );
      log.debug("Stored lineage for eddy {} in RocksDB", eddyId);
    } catch (RocksDBException e) {
      log.error("Failed to put lineage for eddy {} to RocksDB", eddyId, e);
      throw new RuntimeException("RocksDB lineage write failed", e);
    }
  }

  @Override
  public Optional<String> getLineage(String eddyId) {
    if (closed) throw new IllegalStateException("Store is closed");
    try {
      String key = LINEAGE_PREFIX + eddyId;
      byte[] value = db.get(key.getBytes(StandardCharsets.UTF_8));
      if (value == null) {
        return Optional.empty();
      }
      return Optional.of(new String(value, StandardCharsets.UTF_8));
    } catch (RocksDBException e) {
      log.error("Failed to get lineage for eddy {} from RocksDB", eddyId, e);
      return Optional.empty();
    }
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      if (db != null) {
        db.close();
      }
      log.info("Closed RocksDB CRDT store at {}", dataDir);
    }
  }
  
  /**
   * Expose RocksDB instance for additional persistence operations (e.g., rate limiter state).
   */
  public RocksDB getDb() {
    if (closed) throw new IllegalStateException("Store is closed");
    return db;
  }
  
  /**
   * Get estimated disk usage in bytes.
   * Calculates total size of all files in the RocksDB directory.
   */
  public long getDiskUsageBytes() {
    if (closed) return 0;
    
    // Calculate directory size (includes SST, WAL, LOG, and all other files)
    try {
      return Files.walk(dataDir)
        .filter(Files::isRegularFile)
        .mapToLong(p -> {
          try {
            return Files.size(p);
          } catch (Exception e) {
            return 0;
          }
        })
        .sum();
    } catch (Exception e) {
      log.debug("Failed to calculate directory size: {}", e.getMessage());
      return 0;
    }
  }
}
