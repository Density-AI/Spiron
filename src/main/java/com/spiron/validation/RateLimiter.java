package com.spiron.validation;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Token bucket rate limiter with per-peer tracking.
 * Implements crash fault tolerance by limiting request rates and persisting state.
 */
public class RateLimiter {

  private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

  private final ConcurrentHashMap<String, TokenBucket> peerBuckets = new ConcurrentHashMap<>();
  private final int maxRequestsPerSecond;
  private final int burstCapacity;
  private final RateLimitStateStore store;
  private final ScheduledExecutorService persistenceScheduler;

  public RateLimiter(int maxRequestsPerSecond, RateLimitStateStore store) {
    this.maxRequestsPerSecond = maxRequestsPerSecond;
    // Allow burst up to 2x the rate limit
    this.burstCapacity = maxRequestsPerSecond * 2;
    this.store = store;
    
    // Periodic persistence every 5 seconds
    this.persistenceScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "RateLimiter-Persistence");
      t.setDaemon(true);
      return t;
    });
    
    if (store != null) {
      persistenceScheduler.scheduleAtFixedRate(
        this::persistAll,
        5, 5, TimeUnit.SECONDS
      );
    }
  }

  public RateLimiter(int maxRequestsPerSecond) {
    this(maxRequestsPerSecond, null);
  }

  /**
   * Checks if a request from the given peer should be allowed.
   * 
   * @param peerId identifier for the peer (e.g., "host:port")
   * @return true if request is allowed, false if rate limit exceeded
   */
  public boolean allowRequest(String peerId) {
    TokenBucket bucket = peerBuckets.computeIfAbsent(
      peerId, 
      k -> {
        // Try to restore from persistent state
        if (store != null) {
          var saved = store.load(k);
          if (saved.isPresent()) {
            log.debug("Restored rate limit state for peer: {} (tokens={})", k, saved.get().tokens);
            return new TokenBucket(maxRequestsPerSecond, burstCapacity, 
              saved.get().tokens, saved.get().lastRefillTime);
          }
        }
        return new TokenBucket(maxRequestsPerSecond, burstCapacity);
      }
    );
    return bucket.tryConsume();
  }

  /**
   * Get current bucket state for a peer (for metrics/debugging).
   */
  public double getAvailableTokens(String peerId) {
    TokenBucket bucket = peerBuckets.get(peerId);
    return bucket != null ? bucket.getAvailableTokens() : maxRequestsPerSecond;
  }

  /**
   * Clear all rate limit state (for testing).
   */
  public void clear() {
    peerBuckets.clear();
  }

  /**
   * Persist all current rate limit states to RocksDB.
   */
  private void persistAll() {
    if (store == null) return;
    
    int persisted = 0;
    for (var entry : peerBuckets.entrySet()) {
      try {
        TokenBucket bucket = entry.getValue();
        RateLimitStateStore.RateLimitState state = bucket.getState();
        store.save(entry.getKey(), state);
        persisted++;
      } catch (Exception e) {
        log.error("Failed to persist rate limit for peer: {}", entry.getKey(), e);
      }
    }
    
    if (persisted > 0) {
      log.debug("Persisted {} rate limit states", persisted);
    }
  }

  /**
   * Shutdown persistence scheduler.
   */
  public void shutdown() {
    if (persistenceScheduler != null) {
      persistAll(); // Final persistence
      persistenceScheduler.shutdown();
    }
  }

  /**
   * Token bucket implementation for rate limiting.
   * Refills at a steady rate, allows bursts up to capacity.
   */
  private static class TokenBucket {
    private final double refillRate; // tokens per millisecond
    private final double capacity;
    private double tokens;
    private long lastRefillTime;

    TokenBucket(int tokensPerSecond, int capacity) {
      this.refillRate = tokensPerSecond / 1000.0; // Convert to per-ms
      this.capacity = capacity;
      this.tokens = capacity; // Start with full bucket
      this.lastRefillTime = System.currentTimeMillis();
    }

    TokenBucket(int tokensPerSecond, int capacity, double initialTokens, long initialTime) {
      this.refillRate = tokensPerSecond / 1000.0;
      this.capacity = capacity;
      this.tokens = Math.min(capacity, initialTokens);
      this.lastRefillTime = initialTime;
    }

    synchronized boolean tryConsume() {
      refill();
      if (tokens >= 1.0) {
        tokens -= 1.0;
        return true;
      }
      return false;
    }

    synchronized double getAvailableTokens() {
      refill();
      return tokens;
    }

    synchronized RateLimitStateStore.RateLimitState getState() {
      refill();
      return new RateLimitStateStore.RateLimitState(tokens, lastRefillTime);
    }

    private void refill() {
      long now = System.currentTimeMillis();
      long elapsedMs = now - lastRefillTime;
      if (elapsedMs > 0) {
        double tokensToAdd = elapsedMs * refillRate;
        tokens = Math.min(capacity, tokens + tokensToAdd);
        lastRefillTime = now;
      }
    }
  }
}
