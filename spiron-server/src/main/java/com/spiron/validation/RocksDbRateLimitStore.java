package com.spiron.validation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RocksDB-backed rate limiter state store for solo mode.
 * Persists per-peer token buckets for crash recovery.
 */
public class RocksDbRateLimitStore implements RateLimitStateStore {

  private static final Logger log = LoggerFactory.getLogger(RocksDbRateLimitStore.class);
  private static final String KEY_PREFIX = "ratelimit:";

  private final RocksDB db;

  public RocksDbRateLimitStore(RocksDB db) {
    this.db = db;
  }

  @Override
  public void save(String peerId, RateLimitState state) {
    try {
      String key = KEY_PREFIX + peerId;
      byte[] value = serialize(state);
      db.put(key.getBytes(StandardCharsets.UTF_8), value);
    } catch (Exception e) {
      log.error("Failed to save rate limit state for peer: {}", peerId, e);
    }
  }

  @Override
  public Optional<RateLimitState> load(String peerId) {
    try {
      String key = KEY_PREFIX + peerId;
      byte[] value = db.get(key.getBytes(StandardCharsets.UTF_8));
      if (value == null) {
        return Optional.empty();
      }
      return Optional.of(deserialize(value));
    } catch (Exception e) {
      log.error("Failed to load rate limit state for peer: {}", peerId, e);
      return Optional.empty();
    }
  }

  @Override
  public void delete(String peerId) {
    try {
      String key = KEY_PREFIX + peerId;
      db.delete(key.getBytes(StandardCharsets.UTF_8));
    } catch (RocksDBException e) {
      log.error("Failed to delete rate limit state for peer: {}", peerId, e);
    }
  }

  private byte[] serialize(RateLimitState state) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
      oos.writeDouble(state.tokens);
      oos.writeLong(state.lastRefillTime);
      oos.flush();
    }
    return baos.toByteArray();
  }

  private RateLimitState deserialize(byte[] data) throws Exception {
    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
      double tokens = ois.readDouble();
      long lastRefillTime = ois.readLong();
      return new RateLimitState(tokens, lastRefillTime);
    }
  }
}
