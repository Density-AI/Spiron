package com.spiron.metrics;

import static org.junit.jupiter.api.Assertions.*;

import com.spiron.metrics.MetricsRegistry;
import com.spiron.metrics.RpcMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;

public class RpcMetricsTest {

  @Test
  void countersIncrementAndTaggedCountersExist() {
    MetricsRegistry mr = new MetricsRegistry();
    mr.startHttp(0);
    MeterRegistry reg = mr.registry();
    RpcMetrics m = new RpcMetrics(reg);

    // global counters
    m.incBroadcast();
    m.incCommit();
    m.incFailure();

    // peer counters
    m.incBroadcast("peer-a:1234");
    m.incCommit("peer-a:1234");
    m.incFailure("peer-a:1234");

    // Validate via registry
    double g = reg.get("spiron_rpc_broadcast_total").counter().count();
    assertEquals(1.0, g, 0.0001);

    double peerB = reg
      .get("spiron_rpc_broadcast_total")
      .tag("peer", "peer-a:1234")
      .counter()
      .count();
    assertEquals(1.0, peerB, 0.0001);
  }
}
