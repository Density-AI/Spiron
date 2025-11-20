package com.spiron.di;

import com.spiron.metrics.MetricsRegistry;
import com.spiron.metrics.RpcMetrics;
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
public final class SpironModule_ProvideRpcMetricsFactory implements Factory<RpcMetrics> {
  private final SpironModule module;

  private final Provider<MetricsRegistry> metricsProvider;

  public SpironModule_ProvideRpcMetricsFactory(SpironModule module,
      Provider<MetricsRegistry> metricsProvider) {
    this.module = module;
    this.metricsProvider = metricsProvider;
  }

  @Override
  public RpcMetrics get() {
    return provideRpcMetrics(module, metricsProvider.get());
  }

  public static SpironModule_ProvideRpcMetricsFactory create(SpironModule module,
      Provider<MetricsRegistry> metricsProvider) {
    return new SpironModule_ProvideRpcMetricsFactory(module, metricsProvider);
  }

  public static RpcMetrics provideRpcMetrics(SpironModule instance, MetricsRegistry metrics) {
    return Preconditions.checkNotNullFromProvides(instance.provideRpcMetrics(metrics));
  }
}
