package com.spiron.metrics;

import io.micrometer.core.instrument.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Throughput metrics: ingestion rate, bytes in/out per second.
 */
public class ThroughputMetrics {

  private final Counter eddiesIngested;
  private final Counter eddiesEmitted;
  private final Counter bytesIngested;
  private final Counter bytesEmitted;
  private final AtomicLong bytesIngestedPerSecValue = new AtomicLong(0);
  private final AtomicLong bytesEmittedPerSecValue = new AtomicLong(0);
  private final AtomicLong eddiesIngestedPerSecValue = new AtomicLong(0);

  public ThroughputMetrics(MeterRegistry registry) {
    this.eddiesIngested = Counter.builder("spiron_eddies_ingested_total")
      .description("Total number of eddies ingested")
      .register(registry);
      
    this.eddiesEmitted = Counter.builder("spiron_eddies_emitted_total")
      .description("Total number of eddies emitted/committed")
      .register(registry);
      
    this.bytesIngested = Counter.builder("spiron_bytes_ingested_total")
      .description("Total bytes ingested (vector data)")
      .baseUnit("bytes")
      .register(registry);
      
    this.bytesEmitted = Counter.builder("spiron_bytes_emitted_total")
      .description("Total bytes emitted (broadcasts/commits)")
      .baseUnit("bytes")
      .register(registry);
      
    registry.gauge("spiron_bytes_ingested_per_sec",
      Tags.empty(),
      bytesIngestedPerSecValue,
      AtomicLong::get);
      
    registry.gauge("spiron_bytes_emitted_per_sec",
      Tags.empty(),
      bytesEmittedPerSecValue,
      AtomicLong::get);
      
    registry.gauge("spiron_eddies_ingested_per_sec",
      Tags.empty(),
      eddiesIngestedPerSecValue,
      AtomicLong::get);
  }

  public void incEddiesIngested() {
    eddiesIngested.increment();
  }
  
  public void incEddiesEmitted() {
    eddiesEmitted.increment();
  }
  
  public void recordBytesIngested(long bytes) {
    bytesIngested.increment(bytes);
  }
  
  public void recordBytesEmitted(long bytes) {
    bytesEmitted.increment(bytes);
  }
  
  public void setBytesIngestedPerSec(double bytesPerSec) {
    bytesIngestedPerSecValue.set((long) bytesPerSec);
  }
  
  public void setBytesEmittedPerSec(double bytesPerSec) {
    bytesEmittedPerSecValue.set((long) bytesPerSec);
  }
  
  public void setEddiesIngestedPerSec(double eddiesPerSec) {
    eddiesIngestedPerSecValue.set((long) eddiesPerSec);
  }
  
  public double getBytesIngestedTotal() {
    return bytesIngested.count();
  }
  
  public double getBytesEmittedTotal() {
    return bytesEmitted.count();
  }
  
  public double getEddiesIngestedTotal() {
    return eddiesIngested.count();
  }
}
