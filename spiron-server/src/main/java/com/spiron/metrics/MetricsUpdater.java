package com.spiron.metrics;

import com.spiron.storage.RocksDbCRDTStore;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background service that periodically updates gauge metrics (disk usage, throughput rates, etc.)
 */
public class MetricsUpdater {
  
  private static final Logger log = LoggerFactory.getLogger(MetricsUpdater.class);
  
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private final StorageMetrics storageMetrics;
  private final ThroughputMetrics throughputMetrics;
  private final RocksDbCRDTStore crdtStore;
  
  private volatile long lastBytesIngested = 0;
  private volatile long lastBytesEmitted = 0;
  private volatile long lastEddiesIngested = 0;
  private volatile long lastUpdateTime = System.currentTimeMillis();
  
  public MetricsUpdater(
    StorageMetrics storageMetrics,
    ThroughputMetrics throughputMetrics,
    RocksDbCRDTStore crdtStore
  ) {
    this.storageMetrics = storageMetrics;
    this.throughputMetrics = throughputMetrics;
    this.crdtStore = crdtStore;
  }
  
  public void start() {
    // Update metrics every 5 seconds
    scheduler.scheduleAtFixedRate(
      this::updateMetrics,
      0,
      5,
      TimeUnit.SECONDS
    );
    log.info("Started metrics updater (5s interval)");
  }
  
  private void updateMetrics() {
    try {
      // Update disk usage
      if (crdtStore != null && storageMetrics != null) {
        long diskBytes = crdtStore.getDiskUsageBytes();
        storageMetrics.setDiskUsage(diskBytes);
      }
      
      // Calculate throughput rates (per second)
      if (throughputMetrics != null) {
        long now = System.currentTimeMillis();
        double elapsedSec = (now - lastUpdateTime) / 1000.0;
        
        if (elapsedSec > 0) {
          // Note: These would need access to the actual counters to calculate deltas
          // For now, we'll set them based on current values
          // In production, you'd track deltas properly
          throughputMetrics.setBytesIngestedPerSec(0); // Placeholder
          throughputMetrics.setBytesEmittedPerSec(0); // Placeholder
          throughputMetrics.setEddiesIngestedPerSec(0); // Placeholder
        }
        
        lastUpdateTime = now;
      }
    } catch (Exception e) {
      log.debug("Error updating metrics: {}", e.getMessage());
    }
  }
  
  public void stop() {
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
    }
    log.info("Stopped metrics updater");
  }
}
