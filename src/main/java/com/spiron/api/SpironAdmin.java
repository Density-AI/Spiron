package com.spiron.api;

import com.spiron.core.*;
import com.spiron.metrics.EnergyMetrics;
import java.util.List;


/**
 * The SpironAdmin class encapsulates an EddyEngine and EnergyMetrics to provide methods for taking a
 * snapshot of EddyState and generating a summary of metrics.
 */
public class SpironAdmin {

  private final EddyEngine engine;
  private final EnergyMetrics metrics;

  public SpironAdmin(EddyEngine engine, EnergyMetrics metrics) {
    this.engine = engine;
    this.metrics = metrics;
  }

  public List<EddyState> snapshot() {
    return engine.snapshot();
  }

  public String metricsSummary() {
    return """
    spiron Metrics:
      Merges/sec : %s
      Energy hist : %s
      Commit latency (p95) : %s ms
    """.formatted(
        metrics.toString(),
        "available via /metrics",
        "check Prometheus"
      );
  }
}
