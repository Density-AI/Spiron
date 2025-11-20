package com.spiron.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPC-related metrics: broadcast/commit counts, latencies and failures.
 */
public class RpcMetrics {

  private final Counter broadcastTotal;
  private final Counter commitTotal;
  private final Counter rpcFailures;
  private final Timer rpcLatency;
  private final DistributionSummary inFlight;
  private final io.micrometer.core.instrument.MeterRegistry registry;
  
  // Broadcast rejection metrics
  private final Counter broadcastRejectedValidation;
  private final Counter broadcastRejectedDuplicate;
  private final Counter broadcastRejectedRateLimit;
  private final Counter broadcastRejectedPeerAllowlist;
  
  // Cache per-peer counters to avoid re-registering
  private final ConcurrentHashMap<String, Counter> broadcastCounters = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Counter> commitCounters = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Counter> failureCounters = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Timer> latencyTimers = new ConcurrentHashMap<>();

  public RpcMetrics(MeterRegistry registry) {
    this.registry = registry;
    this.broadcastTotal = Counter.builder("spiron_rpc_broadcast_total")
      .description("Total number of broadcast RPCs sent (no peer tag)")
      .register(registry);
    this.commitTotal = Counter.builder("spiron_rpc_commit_total")
      .description("Total number of commit RPCs sent (no peer tag)")
      .register(registry);
    this.rpcFailures = Counter.builder("spiron_rpc_failures_total")
      .description("Total number of failed RPC attempts (no peer tag)")
      .register(registry);
    this.rpcLatency = Timer.builder("spiron_rpc_latency")
      .description("RPC round-trip latency (seconds)")
      .publishPercentiles(0.5, 0.9, 0.99)
      .register(registry);
    this.inFlight = DistributionSummary.builder("spiron_rpc_inflight")
      .description("In-flight RPCs")
      .register(registry);
    
    // Broadcast rejection metrics
    this.broadcastRejectedValidation = Counter.builder("spiron_broadcast_rejected_validation_total")
      .description("Total number of broadcasts rejected due to validation failures")
      .register(registry);
    this.broadcastRejectedDuplicate = Counter.builder("spiron_broadcast_rejected_duplicate_total")
      .description("Total number of broadcasts rejected as duplicates")
      .register(registry);
    this.broadcastRejectedRateLimit = Counter.builder("spiron_broadcast_rejected_ratelimit_total")
      .description("Total number of broadcasts rejected due to rate limiting")
      .register(registry);
    this.broadcastRejectedPeerAllowlist = Counter.builder("spiron_broadcast_rejected_allowlist_total")
      .description("Total number of broadcasts rejected due to peer allowlist")
      .register(registry);
  }

  public void incBroadcast() {
    broadcastTotal.increment();
  }

  public void incCommit() {
    commitTotal.increment();
  }

  public void incFailure() {
    rpcFailures.increment();
  }

  public <T extends Runnable> void recordLatency(Runnable fn) {
    rpcLatency.record(fn);
  }

  public void recordInFlight(double v) {
    inFlight.record(v);
  }

  // Per-peer helpers using a 'peer' tag
  public void incBroadcast(String peer) {
    Counter counter = broadcastCounters.computeIfAbsent(peer, p -> {
      Counter c = Counter.builder("spiron_rpc_broadcast_total")
        .description("Total number of broadcast RPCs sent")
        .tag("peer", p)
        .register(registry);
      System.out.println("Registered broadcast counter for peer: " + p + ", count=" + c.count());
      return c;
    });
    counter.increment();
    System.out.println("Incremented broadcast counter for peer: " + peer + ", count=" + counter.count());
  }

  public void incCommit(String peer) {
    commitCounters.computeIfAbsent(peer, p ->
      Counter.builder("spiron_rpc_commit_total")
        .description("Total number of commit RPCs sent")
        .tag("peer", p)
        .register(registry)
    ).increment();
  }

  public void incFailure(String peer) {
    failureCounters.computeIfAbsent(peer, p ->
      Counter.builder("spiron_rpc_failures_total")
        .description("Total number of failed RPC attempts")
        .tag("peer", p)
        .register(registry)
    ).increment();
  }

  public void recordLatency(String peer, Runnable fn) {
    latencyTimers.computeIfAbsent(peer, p ->
      Timer.builder("spiron_rpc_latency")
        .description("RPC round-trip latency")
        .tag("peer", p)
        .publishPercentiles(0.5, 0.9, 0.99)
        .register(registry)
    ).record(fn);
  }
  
  // Broadcast rejection metric methods
  public void incBroadcastRejectedValidation() {
    broadcastRejectedValidation.increment();
  }
  
  public void incBroadcastRejectedDuplicate() {
    broadcastRejectedDuplicate.increment();
  }
  
  public void incBroadcastRejectedRateLimit() {
    broadcastRejectedRateLimit.increment();
  }
  
  public void incBroadcastRejectedPeerAllowlist() {
    broadcastRejectedPeerAllowlist.increment();
  }
}