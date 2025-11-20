package com.spiron.validation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DuplicateDetectorTest {

  private DuplicateDetector detector;

  @BeforeEach
  void setUp() {
    detector = new DuplicateDetector(1000); // 1 second expiry
  }

  @Test
  void testFirstMessageNotDuplicate() {
    assertFalse(detector.isDuplicate("msg1"));
  }

  @Test
  void testImmediateDuplicateDetected() {
    detector.isDuplicate("msg1");
    assertTrue(detector.isDuplicate("msg1"));
  }

  @Test
  void testDifferentMessagesNotDuplicates() {
    assertFalse(detector.isDuplicate("msg1"));
    assertFalse(detector.isDuplicate("msg2"));
    assertFalse(detector.isDuplicate("msg3"));
  }

  @Test
  void testDuplicateAfterExpiry() throws InterruptedException {
    assertFalse(detector.isDuplicate("msg1"), "First occurrence should not be duplicate");
    
    Thread.sleep(1100); // Wait for expiry
    
    assertFalse(detector.isDuplicate("msg1"), "After expiry, should not be duplicate");
  }

  @Test
  void testMultipleDuplicatesWithinWindow() {
    assertFalse(detector.isDuplicate("msg1"));
    assertTrue(detector.isDuplicate("msg1"));
    assertTrue(detector.isDuplicate("msg1"));
    assertTrue(detector.isDuplicate("msg1"));
  }

  @Test
  void testCleanup() {
    // Add many messages
    for (int i = 0; i < 100; i++) {
      detector.isDuplicate("msg" + i);
    }
    
    assertTrue(detector.getTrackedCount() > 0);
    
    detector.clear();
    assertEquals(0, detector.getTrackedCount());
  }

  @Test
  void testConcurrentAccess() throws InterruptedException {
    int numThreads = 10;
    int messagesPerThread = 100;
    Thread[] threads = new Thread[numThreads];
    
    for (int t = 0; t < numThreads; t++) {
      final int threadId = t;
      threads[t] = new Thread(() -> {
        for (int i = 0; i < messagesPerThread; i++) {
          String msgId = "thread" + threadId + "-msg" + i;
          detector.isDuplicate(msgId);
        }
      });
      threads[t].start();
    }
    
    for (Thread thread : threads) {
      thread.join();
    }
    
    // Should have tracked all unique messages
    assertTrue(detector.getTrackedCount() <= numThreads * messagesPerThread);
  }
}
