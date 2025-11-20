package com.spiron.metrics;

import static org.junit.jupiter.api.Assertions.*;

import com.spiron.core.EddyEngine;
import com.spiron.core.EddyState;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Performance and metrics tests for the Spiron consensus engine.
 */
class PerformanceMetricsTest {

  @Test
  void testMetricsCollection_BasicFlow() {
    PerformanceMetrics metrics = new PerformanceMetrics();
    
    metrics.recordEddyCreated("eddy-1", 128);
    metrics.recordEddyCommitted("eddy-1");
    
    var snapshot = metrics.getSnapshot();
    
    assertEquals(1, snapshot.totalEddysProcessed);
    assertEquals(1, snapshot.totalEddysCommitted);
    assertTrue(snapshot.averageLatencyMs >= 0);
  }

  @Test
  void testMetricsCollection_MultipleEddys() {
    PerformanceMetrics metrics = new PerformanceMetrics();
    
    for (int i = 0; i < 100; i++) {
      String id = "eddy-" + i;
      metrics.recordEddyCreated(id, 256);
      metrics.recordEddyCommitted(id);
    }
    
    var snapshot = metrics.getSnapshot();
    
    assertEquals(100, snapshot.totalEddysProcessed);
    assertEquals(100, snapshot.totalEddysCommitted);
    assertTrue(snapshot.averageLatencyMs >= 0);
    assertTrue(snapshot.throughputEddysPerSecond > 0);
  }

  @Test
  void testLatencyPercentiles() {
    PerformanceMetrics metrics = new PerformanceMetrics();
    
    for (int i = 0; i < 1000; i++) {
      String id = "eddy-" + i;
      metrics.recordEddyCreated(id, 128);
      
      // Simulate varying latencies
      try {
        Thread.sleep(i % 10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      
      metrics.recordEddyCommitted(id);
    }
    
    var snapshot = metrics.getSnapshot();
    
    assertTrue(snapshot.p50LatencyMs > 0);
    assertTrue(snapshot.p95LatencyMs >= snapshot.p50LatencyMs);
    assertTrue(snapshot.p99LatencyMs >= snapshot.p95LatencyMs);
    assertTrue(snapshot.maxLatencyMs >= snapshot.p99LatencyMs);
  }

  @Test
  void testMemoryMetrics() {
    PerformanceMetrics metrics = new PerformanceMetrics();
    
    var snapshot = metrics.getSnapshot();
    
    assertTrue(snapshot.memoryUsedMB > 0);
    assertTrue(snapshot.memoryMaxMB > 0);
    assertTrue(snapshot.memoryUtilizationPercent >= 0);
    assertTrue(snapshot.memoryUtilizationPercent <= 100);
  }

  @Test
  void testCPUMetrics() {
    PerformanceMetrics metrics = new PerformanceMetrics();
    
    var snapshot = metrics.getSnapshot();
    
    assertTrue(snapshot.cpuCount > 0);
    assertTrue(snapshot.threadCount > 0);
  }

  @Test
  void testThroughput_10kEddys() throws Exception {
    PerformanceMetrics metrics = new PerformanceMetrics();
    EddyEngine engine = new EddyEngine(0.85, 0.45, 0.6, 1.0);
    
    int count = 10_000;
    long startTime = System.currentTimeMillis();
    
    for (int i = 0; i < count; i++) {
      EddyState eddy = createRandomEddy(256);
      metrics.recordEddyCreated(eddy.id(), 256);
      engine.ingest(eddy);
      
      if (Math.random() > 0.3) {
        metrics.recordEddyCommitted(eddy.id());
      }
    }
    
    long endTime = System.currentTimeMillis();
    double durationSec = (endTime - startTime) / 1000.0;
    
    var snapshot = metrics.getSnapshot();
    
    System.out.println("\n=== 10K Eddys Benchmark ===");
    System.out.println("Duration: " + String.format("%.2f seconds", durationSec));
    System.out.println("Throughput: " + String.format("%.2f eddys/sec", snapshot.throughputEddysPerSecond));
    System.out.println("Average Latency: " + String.format("%.3f ms", snapshot.averageLatencyMs));
    
    assertEquals(count, snapshot.totalEddysProcessed);
    assertTrue(snapshot.throughputEddysPerSecond > 0);
  }

  @Test
  void testConcurrentMetrics() throws Exception {
    PerformanceMetrics metrics = new PerformanceMetrics();
    EddyEngine engine = new EddyEngine(0.85, 0.45, 0.6, 1.0);
    
    int threadCount = Runtime.getRuntime().availableProcessors();
    int eddysPerThread = 1000;
    
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    List<Future<?>> futures = new ArrayList<>();
    
    for (int t = 0; t < threadCount; t++) {
      Future<?> future = executor.submit(() -> {
        for (int i = 0; i < eddysPerThread; i++) {
          EddyState eddy = createRandomEddy(256);
          metrics.recordEddyCreated(eddy.id(), 256);
          engine.ingest(eddy);
          metrics.recordEddyCommitted(eddy.id());
        }
      });
      futures.add(future);
    }
    
    for (Future<?> future : futures) {
      future.get();
    }
    
    executor.shutdown();
    
    var snapshot = metrics.getSnapshot();
    
    assertEquals(threadCount * eddysPerThread, snapshot.totalEddysProcessed);
    assertEquals(threadCount * eddysPerThread, snapshot.totalEddysCommitted);
  }

  @Test
  void testProfile_LowLatency() {
    // Simulate LOW_LATENCY profile
    EddyEngine engine = new EddyEngine(0.75, 0.3, 0.5, 0.8);
    PerformanceMetrics metrics = new PerformanceMetrics();
    
    for (int i = 0; i < 100; i++) {
      EddyState eddy = createRandomEddy(128);
      metrics.recordEddyCreated(eddy.id(), 128);
      engine.ingest(eddy);
      
      var dominant = engine.dominant();
      if (dominant.isPresent() && dominant.get().energy() >= 0.8) {
        metrics.recordEddyCommitted(dominant.get().id());
      }
    }
    
    var snapshot = metrics.getSnapshot();
    assertTrue(snapshot.totalEddysCommitted > 0, "LOW_LATENCY profile should commit some eddys");
  }

  @Test
  void testProfile_MaxQuorum() {
    // Simulate MAX_QUORUM profile
    EddyEngine engine = new EddyEngine(0.9, 0.5, 0.7, 1.2);
    PerformanceMetrics metrics = new PerformanceMetrics();
    
    for (int i = 0; i < 100; i++) {
      EddyState eddy = createRandomEddy(256);
      metrics.recordEddyCreated(eddy.id(), 256);
      engine.ingest(eddy);
      
      var dominant = engine.dominant();
      if (dominant.isPresent() && dominant.get().energy() >= 1.2) {
        metrics.recordEddyCommitted(dominant.get().id());
      }
    }
    
    var snapshot = metrics.getSnapshot();
    assertTrue(snapshot.totalEddysProcessed == 100);
  }

  @Test
  void testMetricsReset() {
    PerformanceMetrics metrics = new PerformanceMetrics();
    
    for (int i = 0; i < 10; i++) {
      metrics.recordEddyCreated("eddy-" + i, 128);
      metrics.recordEddyCommitted("eddy-" + i);
    }
    
    var snapshot1 = metrics.getSnapshot();
    assertEquals(10, snapshot1.totalEddysProcessed);
    
    metrics.reset();
    
    var snapshot2 = metrics.getSnapshot();
    assertEquals(0, snapshot2.totalEddysProcessed);
    assertEquals(0, snapshot2.totalEddysCommitted);
  }

  @Test
  void testMetricsSnapshot_ToString() {
    PerformanceMetrics metrics = new PerformanceMetrics();
    
    for (int i = 0; i < 10; i++) {
      metrics.recordEddyCreated("eddy-" + i, 256);
      metrics.recordEddyCommitted("eddy-" + i);
    }
    
    var snapshot = metrics.getSnapshot();
    String str = snapshot.toString();
    
    assertNotNull(str);
    assertTrue(str.contains("processed=10"));
    assertTrue(str.contains("committed=10"));
  }

  private EddyState createRandomEddy(int dimensions) {
    Random random = new Random();
    double[] vector = new double[dimensions];
    double magnitude = 0;
    
    for (int i = 0; i < dimensions; i++) {
      vector[i] = random.nextGaussian();
      magnitude += vector[i] * vector[i];
    }
    
    magnitude = Math.sqrt(magnitude);
    for (int i = 0; i < dimensions; i++) {
      vector[i] /= magnitude;
    }
    
    String id = "eddy-" + System.nanoTime() + "-" + random.nextInt(10000);
    double energy = 0.5 + random.nextDouble() * 0.5;
    
    return new EddyState(id, vector, energy, null);
  }
}
