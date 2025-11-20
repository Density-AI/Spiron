package com.spiron.di;

import com.spiron.metrics.MetricsRegistry;
import com.spiron.metrics.ThroughputMetrics;
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
public final class SpironModule_ProvideThroughputMetricsFactory implements Factory<ThroughputMetrics> {
  private final SpironModule module;

  private final Provider<MetricsRegistry> registryProvider;

  public SpironModule_ProvideThroughputMetricsFactory(SpironModule module,
      Provider<MetricsRegistry> registryProvider) {
    this.module = module;
    this.registryProvider = registryProvider;
  }

  @Override
  public ThroughputMetrics get() {
    return provideThroughputMetrics(module, registryProvider.get());
  }

  public static SpironModule_ProvideThroughputMetricsFactory create(SpironModule module,
      Provider<MetricsRegistry> registryProvider) {
    return new SpironModule_ProvideThroughputMetricsFactory(module, registryProvider);
  }

  public static ThroughputMetrics provideThroughputMetrics(SpironModule instance,
      MetricsRegistry registry) {
    return Preconditions.checkNotNullFromProvides(instance.provideThroughputMetrics(registry));
  }
}
