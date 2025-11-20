package com.spiron.validation;

import java.util.Optional;

/**
 * Interface for persisting rate limiter state.
 * Implementations can use RocksDB (solo mode) or etcd (cluster mode).
 */
public interface RateLimitStateStore {

  /**
   * Save rate limit state for a peer.
   */
  void save(String peerId, RateLimitState state);

  /**
   * Load rate limit state for a peer.
   */
  Optional<RateLimitState> load(String peerId);

  /**
   * Delete rate limit state for a peer.
   */
  void delete(String peerId);

  /**
   * Rate limit state snapshot.
   */
  class RateLimitState {
    public final double tokens;
    public final long lastRefillTime;

    public RateLimitState(double tokens, long lastRefillTime) {
      this.tokens = tokens;
      this.lastRefillTime = lastRefillTime;
    }
  }
}
