package com.spiron.metrics;

import io.micrometer.core.instrument.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Storage-related metrics: disk usage, IOps, state store size, etc.
 */
public class StorageMetrics {

  private final Counter bytesWritten;
  private final Counter bytesRead;
  private final Counter writeOps;
  private final Counter readOps;
  private final Timer writeLatency;
  private final Timer readLatency;
  
  private final AtomicLong diskUsage = new AtomicLong(0);
  private final AtomicLong stateEntries = new AtomicLong(0);

  public StorageMetrics(MeterRegistry registry) {
    this.bytesWritten = Counter.builder("spiron_storage_bytes_written_total")
      .description("Total bytes written to storage")
      .baseUnit("bytes")
      .register(registry);
      
    this.bytesRead = Counter.builder("spiron_storage_bytes_read_total")
      .description("Total bytes read from storage")
      .baseUnit("bytes")
      .register(registry);
      
    this.writeOps = Counter.builder("spiron_storage_write_ops_total")
      .description("Total number of write operations")
      .register(registry);
      
    this.readOps = Counter.builder("spiron_storage_read_ops_total")
      .description("Total number of read operations")
      .register(registry);
      
    registry.gauge("spiron_storage_disk_usage_bytes",
      Tags.empty(),
      diskUsage,
      AtomicLong::get);
      
    registry.gauge("spiron_storage_state_entries",
      Tags.empty(),
      stateEntries,
      AtomicLong::get);
      
    this.writeLatency = Timer.builder("spiron_storage_write_latency")
      .description("Storage write operation latency")
      .publishPercentiles(0.5, 0.9, 0.99)
      .register(registry);
      
    this.readLatency = Timer.builder("spiron_storage_read_latency")
      .description("Storage read operation latency")
      .publishPercentiles(0.5, 0.9, 0.99)
      .register(registry);
  }

  public void recordBytesWritten(long bytes) {
    bytesWritten.increment(bytes);
  }
  
  public void recordBytesRead(long bytes) {
    bytesRead.increment(bytes);
  }
  
  public void incWriteOps() {
    writeOps.increment();
  }
  
  public void incReadOps() {
    readOps.increment();
  }
  
  public void setDiskUsage(long bytes) {
    diskUsage.set(bytes);
  }
  
  public void setStateEntries(long count) {
    stateEntries.set(count);
  }
  
  public void recordWrite(Runnable fn) {
    writeLatency.record(fn);
  }
  
  public void recordRead(Runnable fn) {
    readLatency.record(fn);
  }
}
