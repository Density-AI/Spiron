package com.spiron.di;

import com.spiron.config.SpironConfig;
import com.spiron.metrics.MetricsRegistry;
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
public final class SpironModule_ProvideMetricsRegistryFactory implements Factory<MetricsRegistry> {
  private final SpironModule module;

  private final Provider<SpironConfig> cfgProvider;

  public SpironModule_ProvideMetricsRegistryFactory(SpironModule module,
      Provider<SpironConfig> cfgProvider) {
    this.module = module;
    this.cfgProvider = cfgProvider;
  }

  @Override
  public MetricsRegistry get() {
    return provideMetricsRegistry(module, cfgProvider.get());
  }

  public static SpironModule_ProvideMetricsRegistryFactory create(SpironModule module,
      Provider<SpironConfig> cfgProvider) {
    return new SpironModule_ProvideMetricsRegistryFactory(module, cfgProvider);
  }

  public static MetricsRegistry provideMetricsRegistry(SpironModule instance, SpironConfig cfg) {
    return Preconditions.checkNotNullFromProvides(instance.provideMetricsRegistry(cfg));
  }
}
