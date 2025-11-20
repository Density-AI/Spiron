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
  private final AtomicLong bytesIngestedPerSec = new AtomicLong(0);
  private final AtomicLong bytesEmittedPerSec = new AtomicLong(0);
  private final AtomicLong eddiesIngestedPerSec = new AtomicLong(0);

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
      bytesIngestedPerSec,
      AtomicLong::get);
      
    registry.gauge("spiron_bytes_emitted_per_sec",
      Tags.empty(),
      bytesEmittedPerSec,
      AtomicLong::get);
      
    registry.gauge("spiron_eddies_ingested_per_sec",
      Tags.empty(),
      eddiesIngestedPerSec,
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
  
  public void setBytesIngestedPerSec(long bytesPerSec) {
    bytesIngestedPerSec.set(bytesPerSec);
  }
  
  public void setBytesEmittedPerSec(long bytesPerSec) {
    bytesEmittedPerSec.set(bytesPerSec);
  }
  
  public void setEddiesIngestedPerSec(long eddiesPerSec) {
    eddiesIngestedPerSec.set(eddiesPerSec);
  }
}
