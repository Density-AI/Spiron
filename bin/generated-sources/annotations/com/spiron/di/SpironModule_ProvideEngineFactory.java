package com.spiron.di;

import com.spiron.config.SpironConfig;
import com.spiron.core.EddyEngine;
import com.spiron.core.LineageTracker;
import com.spiron.core.SpironRaftLog;
import com.spiron.core.SpironSnapshotStore;
import com.spiron.metrics.EnergyMetrics;
import com.spiron.metrics.StorageMetrics;
import com.spiron.metrics.ThroughputMetrics;
import com.spiron.network.RpcClient;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class SpironModule_ProvideEngineFactory implements Factory<EddyEngine> {
  private final SpironModule module;

  private final Provider<SpironConfig> cfgProvider;

  private final Provider<SpironRaftLog> logProvider;

  private final Provider<SpironSnapshotStore> storeProvider;

  private final Provider<RpcClient> clientProvider;

  private final Provider<EnergyMetrics> energyMetricsProvider;

  private final Provider<ThroughputMetrics> throughputMetricsProvider;

  private final Provider<StorageMetrics> storageMetricsProvider;

  private final Provider<LineageTracker> lineageTrackerProvider;

  public SpironModule_ProvideEngineFactory(SpironModule module, Provider<SpironConfig> cfgProvider,
      Provider<SpironRaftLog> logProvider, Provider<SpironSnapshotStore> storeProvider,
      Provider<RpcClient> clientProvider, Provider<EnergyMetrics> energyMetricsProvider,
      Provider<ThroughputMetrics> throughputMetricsProvider,
      Provider<StorageMetrics> storageMetricsProvider,
      Provider<LineageTracker> lineageTrackerProvider) {
    this.module = module;
    this.cfgProvider = cfgProvider;
    this.logProvider = logProvider;
    this.storeProvider = storeProvider;
    this.clientProvider = clientProvider;
    this.energyMetricsProvider = energyMetricsProvider;
    this.throughputMetricsProvider = throughputMetricsProvider;
    this.storageMetricsProvider = storageMetricsProvider;
    this.lineageTrackerProvider = lineageTrackerProvider;
  }

  @Override
  public EddyEngine get() {
    return provideEngine(module, cfgProvider.get(), logProvider.get(), storeProvider.get(), clientProvider.get(), energyMetricsProvider.get(), throughputMetricsProvider.get(), storageMetricsProvider.get(), lineageTrackerProvider.get());
  }

  public static SpironModule_ProvideEngineFactory create(SpironModule module,
      Provider<SpironConfig> cfgProvider, Provider<SpironRaftLog> logProvider,
      Provider<SpironSnapshotStore> storeProvider, Provider<RpcClient> clientProvider,
      Provider<EnergyMetrics> energyMetricsProvider,
      Provider<ThroughputMetrics> throughputMetricsProvider,
      Provider<StorageMetrics> storageMetricsProvider,
      Provider<LineageTracker> lineageTrackerProvider) {
    return new SpironModule_ProvideEngineFactory(module, cfgProvider, logProvider, storeProvider, clientProvider, energyMetricsProvider, throughputMetricsProvider, storageMetricsProvider, lineageTrackerProvider);
  }

  public static EddyEngine provideEngine(SpironModule instance, SpironConfig cfg, SpironRaftLog log,
      SpironSnapshotStore store, RpcClient client, EnergyMetrics energyMetrics,
      ThroughputMetrics throughputMetrics, StorageMetrics storageMetrics,
      LineageTracker lineageTracker) {
    return Preconditions.checkNotNullFromProvides(instance.provideEngine(cfg, log, store, client, energyMetrics, throughputMetrics, storageMetrics, lineageTracker));
  }
}
