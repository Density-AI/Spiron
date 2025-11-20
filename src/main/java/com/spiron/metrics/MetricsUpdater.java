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
      
      // Calculate throughput rates (per second) using actual counter deltas
      if (throughputMetrics != null) {
        long now = System.currentTimeMillis();
        double elapsedSec = (now - lastUpdateTime) / 1000.0;
        
        if (elapsedSec > 0) {
          // Get current counter values
          double currentBytesIngested = throughputMetrics.getBytesIngestedTotal();
          double currentBytesEmitted = throughputMetrics.getBytesEmittedTotal();
          double currentEddiesIngested = throughputMetrics.getEddiesIngestedTotal();
          
          // Calculate rates (delta / time)
          double bytesIngestedRate = (currentBytesIngested - lastBytesIngested) / elapsedSec;
          double bytesEmittedRate = (currentBytesEmitted - lastBytesEmitted) / elapsedSec;
          double eddiesIngestedRate = (currentEddiesIngested - lastEddiesIngested) / elapsedSec;
          
          // Update gauge values
          throughputMetrics.setBytesIngestedPerSec(bytesIngestedRate);
          throughputMetrics.setBytesEmittedPerSec(bytesEmittedRate);
          throughputMetrics.setEddiesIngestedPerSec(eddiesIngestedRate);
          
          // Store current values for next iteration
          lastBytesIngested = (long) currentBytesIngested;
          lastBytesEmitted = (long) currentBytesEmitted;
          lastEddiesIngested = (long) currentEddiesIngested;
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
