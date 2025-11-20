package com.spiron.storage;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.DeleteOption;
import io.etcd.jetcd.options.GetOption;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * etcd-backed CRDT store for cluster mode.
 * Provides distributed, consistent CRDT state across multiple nodes.
 * Thread-safe implementation with proper timeout handling.
 */
public class EtcdCRDTStore implements CRDTStore {

  private static final Logger log = LoggerFactory.getLogger(
    EtcdCRDTStore.class
  );

  private static final String KEY_PREFIX = "spiron/eddy/";
  private static final String LINEAGE_PREFIX = "spiron/lineage/";
  private static final long OPERATION_TIMEOUT_SECONDS = 5;

  private final String etcdEndpoints;
  private final Client client;
  private final KV kvClient;
  private volatile boolean closed = false;

  /**
   * @param etcdEndpoints comma-separated etcd server URLs, e.g. "http://localhost:2379,http://localhost:2380"
   */
  public EtcdCRDTStore(String etcdEndpoints) {
    this.etcdEndpoints = etcdEndpoints;
    try {
      this.client = Client.builder()
        .endpoints(etcdEndpoints.split(","))
        .build();
      this.kvClient = client.getKVClient();
      log.info("Initialized EtcdCRDTStore connected to: {}", etcdEndpoints);
    } catch (Exception e) {
      throw new RuntimeException(
        "Failed to connect to etcd at " + etcdEndpoints,
        e
      );
    }
  }

  @Override
  public void put(String eddyId, String eddyJsonState) {
    if (closed) throw new IllegalStateException("Store is closed");
    try {
      ByteSequence key = ByteSequence.from(
        KEY_PREFIX + eddyId,
        StandardCharsets.UTF_8
      );
      ByteSequence value = ByteSequence.from(
        eddyJsonState,
        StandardCharsets.UTF_8
      );
      
      kvClient.put(key, value)
        .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      
      log.debug("Stored eddy {} in etcd", eddyId);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      log.error("Failed to put eddy {} to etcd", eddyId, e);
      throw new RuntimeException("etcd write failed", e);
    }
  }

  @Override
  public Optional<String> get(String eddyId) {
    if (closed) throw new IllegalStateException("Store is closed");
    try {
      ByteSequence key = ByteSequence.from(
        KEY_PREFIX + eddyId,
        StandardCharsets.UTF_8
      );
      
      GetResponse response = kvClient.get(key)
        .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      
      if (response.getKvs().isEmpty()) {
        return Optional.empty();
      }
      
      String value = response.getKvs().get(0)
        .getValue()
        .toString(StandardCharsets.UTF_8);
      return Optional.of(value);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      log.error("Failed to get eddy {} from etcd", eddyId, e);
      return Optional.empty();
    }
  }

  @Override
  public Map<String, String> getAll() {
    if (closed) throw new IllegalStateException("Store is closed");
    try {
      ByteSequence prefix = ByteSequence.from(
        KEY_PREFIX,
        StandardCharsets.UTF_8
      );
      
      GetOption option = GetOption.builder()
        .isPrefix(true)
        .build();
      
      GetResponse response = kvClient.get(prefix, option)
        .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      
      Map<String, String> result = new HashMap<>();
      for (KeyValue kv : response.getKvs()) {
        String fullKey = kv.getKey().toString(StandardCharsets.UTF_8);
        String eddyId = fullKey.substring(KEY_PREFIX.length());
        String value = kv.getValue().toString(StandardCharsets.UTF_8);
        result.put(eddyId, value);
      }
      
      return result;
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      log.error("Failed to scan etcd", e);
      return Collections.emptyMap();
    }
  }

  @Override
  public void delete(String eddyId) {
    if (closed) throw new IllegalStateException("Store is closed");
    try {
      ByteSequence key = ByteSequence.from(
        KEY_PREFIX + eddyId,
        StandardCharsets.UTF_8
      );
      
      kvClient.delete(key)
        .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      
      log.debug("Deleted eddy {} from etcd", eddyId);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      log.error("Failed to delete eddy {} from etcd", eddyId, e);
      throw new RuntimeException("etcd delete failed", e);
    }
  }

  @Override
  public void clear() {
    if (closed) throw new IllegalStateException("Store is closed");
    log.warn("Clearing all CRDT state from etcd");
    try {
      ByteSequence prefix = ByteSequence.from(
        KEY_PREFIX,
        StandardCharsets.UTF_8
      );
      
      DeleteOption option = DeleteOption.builder()
        .isPrefix(true)
        .build();
      
      DeleteResponse response = kvClient.delete(prefix, option)
        .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      
      log.info("Cleared {} entries from etcd", response.getDeleted());
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      log.error("Failed to clear etcd", e);
      throw new RuntimeException("etcd clear failed", e);
    }
  }

  @Override
  public boolean exists(String eddyId) {
    if (closed) throw new IllegalStateException("Store is closed");
    try {
      ByteSequence key = ByteSequence.from(
        KEY_PREFIX + eddyId,
        StandardCharsets.UTF_8
      );
      
      GetResponse response = kvClient.get(key)
        .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      
      return !response.getKvs().isEmpty();
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      log.error("Failed to check existence of eddy {} in etcd", eddyId, e);
      return false;
    }
  }

  @Override
  public void putLineage(String eddyId, String lineageJson) {
    if (closed) throw new IllegalStateException("Store is closed");
    try {
      ByteSequence key = ByteSequence.from(
        LINEAGE_PREFIX + eddyId,
        StandardCharsets.UTF_8
      );
      ByteSequence value = ByteSequence.from(
        lineageJson,
        StandardCharsets.UTF_8
      );
      
      kvClient.put(key, value)
        .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      
      log.debug("Stored lineage for eddy {} in etcd", eddyId);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      log.error("Failed to put lineage for eddy {} to etcd", eddyId, e);
      throw new RuntimeException("etcd lineage write failed", e);
    }
  }

  @Override
  public Optional<String> getLineage(String eddyId) {
    if (closed) throw new IllegalStateException("Store is closed");
    try {
      ByteSequence key = ByteSequence.from(
        LINEAGE_PREFIX + eddyId,
        StandardCharsets.UTF_8
      );
      
      GetResponse response = kvClient.get(key)
        .get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      
      if (response.getKvs().isEmpty()) {
        return Optional.empty();
      }
      
      String value = response.getKvs().get(0)
        .getValue()
        .toString(StandardCharsets.UTF_8);
      return Optional.of(value);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      log.error("Failed to get lineage for eddy {} from etcd", eddyId, e);
      return Optional.empty();
    }
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      try {
        if (kvClient != null) {
          kvClient.close();
        }
        if (client != null) {
          client.close();
        }
        log.info("Closed etcd CRDT store ({})", etcdEndpoints);
      } catch (Exception e) {
        log.error("Error closing etcd client", e);
      }
    }
  }

  /**
   * Expose etcd Client for additional persistence operations (e.g., rate limiter state).
   */
  public Client getClient() {
    if (closed) throw new IllegalStateException("Store is closed");
    return client;
  }
}
