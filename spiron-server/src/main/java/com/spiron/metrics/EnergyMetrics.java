package com.spiron.metrics;

import io.micrometer.core.instrument.*;

/** Tracks merge frequency, energy histogram, commit latency, and CRDT operations. */
public class EnergyMetrics {

  private final Counter merges;
  private final Counter crdtIngests;
  private final Counter crdtCommits;
  private final Counter crdtDampings;
  private final DistributionSummary energyLevels;
  private final Timer commitLatency;
  private final Timer mergeLatency;

  public EnergyMetrics(MeterRegistry registry) {
    this.merges = Counter.builder("spiron_merges_total")
      .description("Total number of vortex merges")
      .register(registry);
      
    this.crdtIngests = Counter.builder("spiron_crdt_ingests_total")
      .description("Total number of CRDT ingest operations")
      .register(registry);
      
    this.crdtCommits = Counter.builder("spiron_crdt_commits_total")
      .description("Total number of CRDT commit operations")
      .register(registry);
      
    this.crdtDampings = Counter.builder("spiron_crdt_dampings_total")
      .description("Total number of energy damping operations")
      .register(registry);
      
    this.energyLevels = DistributionSummary.builder("spiron_energy_levels")
      .description("Energy level distribution of eddies")
      .register(registry);
      
    this.commitLatency = Timer.builder("spiron_commit_latency")
      .description("Latency to reach dominance and commit")
      .register(registry);
      
    this.mergeLatency = Timer.builder("spiron_merge_latency")
      .description("Latency of merge operations")
      .publishPercentiles(0.5, 0.9, 0.99)
      .register(registry);
  }

  public void incMerge() {
    merges.increment();
  }
  
  public void incCrdtIngest() {
    crdtIngests.increment();
  }
  
  public void incCrdtCommit() {
    crdtCommits.increment();
  }
  
  public void incCrdtDamping() {
    crdtDampings.increment();
  }

  public void recordEnergy(double e) {
    energyLevels.record(e);
  }

  public void recordCommit(Runnable fn) {
    commitLatency.record(fn);
  }
  
  public void recordMerge(Runnable fn) {
    mergeLatency.record(fn);
  }
}
