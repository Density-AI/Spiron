package com.spiron.validation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

  private RateLimiter rateLimiter;

  @BeforeEach
  void setUp() {
    rateLimiter = new RateLimiter(10); // 10 requests per second
  }

  @Test
  void testFirstRequestAllowed() {
    assertTrue(rateLimiter.allowRequest("peer1"));
  }

  @Test
  void testBurstAllowed() {
    // Burst capacity is 2x rate limit = 20 tokens
    int allowedCount = 0;
    for (int i = 0; i < 25; i++) {
      if (rateLimiter.allowRequest("peer1")) {
        allowedCount++;
      }
    }
    
    // Should allow at least the burst capacity
    assertTrue(allowedCount >= 20, "Expected at least 20 allowed, got " + allowedCount);
    // Should not allow all 25
    assertTrue(allowedCount < 25, "Expected some rejections, got " + allowedCount);
  }

  @Test
  void testRateLimitRecovery() throws InterruptedException {
    // Exhaust tokens
    for (int i = 0; i < 30; i++) {
      rateLimiter.allowRequest("peer1");
    }
    
    // Should be rate limited
    assertFalse(rateLimiter.allowRequest("peer1"));
    
    // Wait for token refill (100ms should add 1 token at 10/sec rate)
    Thread.sleep(150);
    
    // Should allow at least one request
    assertTrue(rateLimiter.allowRequest("peer1"));
  }

  @Test
  void testPerPeerIsolation() {
    // Exhaust peer1's tokens
    for (int i = 0; i < 30; i++) {
      rateLimiter.allowRequest("peer1");
    }
    
    // peer1 should be rate limited
    assertFalse(rateLimiter.allowRequest("peer1"));
    
    // peer2 should still be allowed
    assertTrue(rateLimiter.allowRequest("peer2"));
    assertTrue(rateLimiter.allowRequest("peer3"));
  }

  @Test
  void testClear() {
    rateLimiter.allowRequest("peer1");
    rateLimiter.allowRequest("peer2");
    
    rateLimiter.clear();
    
    // After clear, all peers should have fresh buckets
    assertTrue(rateLimiter.allowRequest("peer1"));
    assertTrue(rateLimiter.allowRequest("peer2"));
  }

  @Test
  void testGetAvailableTokens() {
    // For a new peer that hasn't made requests yet, returns rate limit
    double beforeFirstRequest = rateLimiter.getAvailableTokens("new-peer");
    assertEquals(10.0, beforeFirstRequest, 0.1, "Uninitializedfuture should return rate limit");
    
    // After first request, bucket is created with burst capacity
    rateLimiter.allowRequest("new-peer");
    
    // Now tokens should be slightly less than burst capacity (20 - 1)
    double afterFirstRequest = rateLimiter.getAvailableTokens("new-peer");
    assertTrue(afterFirstRequest < 20.0 && afterFirstRequest >= 18.0, 
      "After consuming 1 token from burst, should have ~19 tokens");
  }
}
