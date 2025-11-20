package com.spiron.metrics;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Comprehensive performance metrics collection for Spiron consensus engine.
 * Tracks latency, throughput, memory, and CPU utilization.
 */
public class PerformanceMetrics {
  
  private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
  private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
  
  private final LongAdder totalEddysProcessed = new LongAdder();
  private final LongAdder totalEddysCommitted = new LongAdder();
  private final AtomicLong totalLatencyNs = new AtomicLong(0);
  
  private final ConcurrentHashMap<String, EddyMetric> eddyMetrics = new ConcurrentHashMap<>();
  
  private final long startTime = System.currentTimeMillis();
  
  public static class EddyMetric {
    public final String eddyId;
    public final long createdAtNs;
    public long committedAtNs;
    public long latencyNs;
    public int vectorDimensions;
    public String state;
    
    public EddyMetric(String eddyId, long createdAtNs) {
      this.eddyId = eddyId;
      this.createdAtNs = createdAtNs;
      this.state = "CREATED";
    }
  }
  
  public void recordEddyCreated(String eddyId, int dimensions) {
    EddyMetric metric = new EddyMetric(eddyId, System.nanoTime());
    metric.vectorDimensions = dimensions;
    eddyMetrics.put(eddyId, metric);
    totalEddysProcessed.increment();
  }
  
  public void recordEddyCommitted(String eddyId) {
    EddyMetric metric = eddyMetrics.get(eddyId);
    if (metric != null) {
      long now = System.nanoTime();
      metric.committedAtNs = now;
      metric.latencyNs = now - metric.createdAtNs;
      metric.state = "COMMITTED";
      
      totalLatencyNs.addAndGet(metric.latencyNs);
      totalEddysCommitted.increment();
    }
  }
  
  public void recordEddyState(String eddyId, String state) {
    EddyMetric metric = eddyMetrics.get(eddyId);
    if (metric != null) {
      metric.state = state;
    }
  }
  
  public MetricsSnapshot getSnapshot() {
    MetricsSnapshot snapshot = new MetricsSnapshot();
    
    // Latency metrics
    long committed = totalEddysCommitted.sum();
    snapshot.totalEddysProcessed = totalEddysProcessed.sum();
    snapshot.totalEddysCommitted = committed;
    snapshot.averageLatencyMs = committed > 0 ? 
      (totalLatencyNs.get() / committed) / 1_000_000.0 : 0;
    
    // Throughput metrics
    long uptimeMs = System.currentTimeMillis() - startTime;
    snapshot.uptimeSeconds = uptimeMs / 1000.0;
    snapshot.throughputEddysPerSecond = uptimeMs > 0 ? 
      (committed * 1000.0) / uptimeMs : 0;
    
    // Memory metrics
    long usedMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
    long maxMemory = memoryMXBean.getHeapMemoryUsage().getMax();
    snapshot.memoryUsedMB = usedMemory / (1024.0 * 1024.0);
    snapshot.memoryMaxMB = maxMemory / (1024.0 * 1024.0);
    snapshot.memoryUtilizationPercent = (usedMemory * 100.0) / maxMemory;
    
    // CPU metrics
    snapshot.cpuCount = Runtime.getRuntime().availableProcessors();
    snapshot.threadCount = threadMXBean.getThreadCount();
    
    // Calculate percentiles
    long[] latencies = eddyMetrics.values().stream()
      .filter(m -> m.latencyNs > 0)
      .mapToLong(m -> m.latencyNs)
      .sorted()
      .toArray();
    
    if (latencies.length > 0) {
      snapshot.p50LatencyMs = latencies[latencies.length / 2] / 1_000_000.0;
      snapshot.p95LatencyMs = latencies[(int)(latencies.length * 0.95)] / 1_000_000.0;
      snapshot.p99LatencyMs = latencies[(int)(latencies.length * 0.99)] / 1_000_000.0;
      snapshot.minLatencyMs = latencies[0] / 1_000_000.0;
      snapshot.maxLatencyMs = latencies[latencies.length - 1] / 1_000_000.0;
    }
    
    return snapshot;
  }
  
  public void reset() {
    eddyMetrics.clear();
    totalEddysProcessed.reset();
    totalEddysCommitted.reset();
    totalLatencyNs.set(0);
  }
  
  public static class MetricsSnapshot {
    public long totalEddysProcessed;
    public long totalEddysCommitted;
    public double averageLatencyMs;
    public double p50LatencyMs;
    public double p95LatencyMs;
    public double p99LatencyMs;
    public double minLatencyMs;
    public double maxLatencyMs;
    public double throughputEddysPerSecond;
    public double memoryUsedMB;
    public double memoryMaxMB;
    public double memoryUtilizationPercent;
    public int cpuCount;
    public int threadCount;
    public double uptimeSeconds;
    
    @Override
    public String toString() {
      return String.format(
        "MetricsSnapshot{processed=%d, committed=%d, avgLatency=%.3fms, " +
        "p50=%.3fms, p95=%.3fms, p99=%.3fms, throughput=%.2f/s, " +
        "memory=%.2f/%.2fMB (%.2f%%), cpus=%d, threads=%d, uptime=%.2fs}",
        totalEddysProcessed, totalEddysCommitted, averageLatencyMs,
        p50LatencyMs, p95LatencyMs, p99LatencyMs, throughputEddysPerSecond,
        memoryUsedMB, memoryMaxMB, memoryUtilizationPercent,
        cpuCount, threadCount, uptimeSeconds
      );
    }
  }
}
