package com.spiron.validation;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * etcd-backed rate limiter state store for cluster mode.
 * Persists per-peer token buckets across distributed nodes.
 */
public class EtcdRateLimitStore implements RateLimitStateStore {

  private static final Logger log = LoggerFactory.getLogger(EtcdRateLimitStore.class);
  private static final String KEY_PREFIX = "spiron/ratelimit/";
  private static final long OPERATION_TIMEOUT_SECONDS = 2;

  private final KV kvClient;

  public EtcdRateLimitStore(Client etcdClient) {
    this.kvClient = etcdClient.getKVClient();
  }

  @Override
  public void save(String peerId, RateLimitState state) {
    try {
      String key = KEY_PREFIX + peerId;
      byte[] value = serialize(state);
      kvClient.put(
        ByteSequence.from(key, StandardCharsets.UTF_8),
        ByteSequence.from(value)
      ).get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.error("Failed to save rate limit state for peer: {}", peerId, e);
    }
  }

  @Override
  public Optional<RateLimitState> load(String peerId) {
    try {
      String key = KEY_PREFIX + peerId;
      var response = kvClient.get(
        ByteSequence.from(key, StandardCharsets.UTF_8)
      ).get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      
      if (response.getKvs().isEmpty()) {
        return Optional.empty();
      }
      
      byte[] value = response.getKvs().get(0).getValue().getBytes();
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
      kvClient.delete(
        ByteSequence.from(key, StandardCharsets.UTF_8)
      ).get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (Exception e) {
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
