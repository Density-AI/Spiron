package com.spiron.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

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
    Counter.builder("spiron_rpc_broadcast_total")
      .description("Total number of broadcast RPCs sent")
      .tag("peer", peer)
      .register(registry)
      .increment();
  }

  public void incCommit(String peer) {
    Counter.builder("spiron_rpc_commit_total")
      .description("Total number of commit RPCs sent")
      .tag("peer", peer)
      .register(registry)
      .increment();
  }

  public void incFailure(String peer) {
    Counter.builder("spiron_rpc_failures_total")
      .description("Total number of failed RPC attempts")
      .tag("peer", peer)
      .register(registry)
      .increment();
  }

  public void recordLatency(String peer, Runnable fn) {
    Timer.builder("spiron_rpc_latency")
      .description("RPC round-trip latency")
      .tag("peer", peer)
      .publishPercentiles(0.5, 0.9, 0.99)
      .register(registry)
      .record(fn);
  }
}
