package com.spiron.di;

import com.spiron.metrics.MetricsUpdater;
import com.spiron.metrics.StorageMetrics;
import com.spiron.metrics.ThroughputMetrics;
import com.spiron.storage.CRDTStore;
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
public final class SpironModule_ProvideMetricsUpdaterFactory implements Factory<MetricsUpdater> {
  private final SpironModule module;

  private final Provider<StorageMetrics> storageMetricsProvider;

  private final Provider<ThroughputMetrics> throughputMetricsProvider;

  private final Provider<CRDTStore> crdtStoreProvider;

  public SpironModule_ProvideMetricsUpdaterFactory(SpironModule module,
      Provider<StorageMetrics> storageMetricsProvider,
      Provider<ThroughputMetrics> throughputMetricsProvider,
      Provider<CRDTStore> crdtStoreProvider) {
    this.module = module;
    this.storageMetricsProvider = storageMetricsProvider;
    this.throughputMetricsProvider = throughputMetricsProvider;
    this.crdtStoreProvider = crdtStoreProvider;
  }

  @Override
  public MetricsUpdater get() {
    return provideMetricsUpdater(module, storageMetricsProvider.get(), throughputMetricsProvider.get(), crdtStoreProvider.get());
  }

  public static SpironModule_ProvideMetricsUpdaterFactory create(SpironModule module,
      Provider<StorageMetrics> storageMetricsProvider,
      Provider<ThroughputMetrics> throughputMetricsProvider,
      Provider<CRDTStore> crdtStoreProvider) {
    return new SpironModule_ProvideMetricsUpdaterFactory(module, storageMetricsProvider, throughputMetricsProvider, crdtStoreProvider);
  }

  public static MetricsUpdater provideMetricsUpdater(SpironModule instance,
      StorageMetrics storageMetrics, ThroughputMetrics throughputMetrics, CRDTStore crdtStore) {
    return Preconditions.checkNotNullFromProvides(instance.provideMetricsUpdater(storageMetrics, throughputMetrics, crdtStore));
  }
}
