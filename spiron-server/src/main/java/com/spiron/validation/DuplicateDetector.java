package com.spiron.validation;

import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects duplicate broadcast messages with time-based expiry.
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class DuplicateDetector {

  private static final Logger log = LoggerFactory.getLogger(DuplicateDetector.class);

  private final ConcurrentHashMap<String, Long> seenMessages = new ConcurrentHashMap<>();
  private final long expiryMillis;
  private volatile long lastCleanupTime = System.currentTimeMillis();
  private static final long CLEANUP_INTERVAL_MS = 60_000; // Cleanup every minute

  public DuplicateDetector(long expiryMillis) {
    this.expiryMillis = expiryMillis;
  }

  /**
   * Checks if a message ID has been seen recently (within expiry window).
   * If not seen, records it for future duplicate detection.
   * 
   * @param messageId unique identifier for the message
   * @return true if duplicate (already seen), false if new
   */
  public boolean isDuplicate(String messageId) {
    long now = System.currentTimeMillis();
    
    // Periodic cleanup of expired entries
    if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
      cleanup(now);
    }

    // putIfAbsent returns null if key was absent, returns existing value if present
    Long previousTimestamp = seenMessages.putIfAbsent(messageId, now);
    
    if (previousTimestamp == null) {
      // New message, not a duplicate
      return false;
    }
    
    // Check if previous entry has expired
    if (now - previousTimestamp > expiryMillis) {
      // Expired, update timestamp and treat as new
      seenMessages.put(messageId, now);
      return false;
    }
    
    // Still within expiry window, this is a duplicate
    return true;
  }

  /**
   * Remove expired entries to prevent unbounded memory growth.
   */
  private void cleanup(long now) {
    lastCleanupTime = now;
    int initialSize = seenMessages.size();
    
    seenMessages.entrySet().removeIf(entry -> 
      now - entry.getValue() > expiryMillis
    );
    
    int removed = initialSize - seenMessages.size();
    if (removed > 0) {
      log.debug("Cleaned up {} expired duplicate detection entries", removed);
    }
  }

  /**
   * Get current count of tracked messages (for metrics/debugging).
   */
  public int getTrackedCount() {
    return seenMessages.size();
  }

  /**
   * Clear all tracked messages (for testing).
   */
  public void clear() {
    seenMessages.clear();
  }
}
