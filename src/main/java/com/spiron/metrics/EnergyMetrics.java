package com.spiron.metrics;

import io.micrometer.core.instrument.*;

/** Tracks merge frequency, energy histogram, and commit latency. */
public class EnergyMetrics {

  private final Counter merges;
  private final DistributionSummary energyLevels;
  private final Timer commitLatency;

  public EnergyMetrics(MeterRegistry registry) {
    this.merges = Counter.builder("spiron_merges_total")
      .description("Total number of vortex merges")
      .register(registry);
    this.energyLevels = DistributionSummary.builder("spiron_energy_levels")
      .description("Energy level distribution of eddies")
      .register(registry);
    this.commitLatency = Timer.builder("spiron_commit_latency")
      .description("Latency to reach dominance and commit")
      .register(registry);
  }

  public void incMerge() {
    merges.increment();
  }

  public void recordEnergy(double e) {
    energyLevels.record(e);
  }

  public void recordCommit(Runnable fn) {
    commitLatency.record(fn);
  }
}
