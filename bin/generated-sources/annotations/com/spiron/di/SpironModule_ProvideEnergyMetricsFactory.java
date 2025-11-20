package com.spiron.di;

import com.spiron.metrics.EnergyMetrics;
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
public final class SpironModule_ProvideEnergyMetricsFactory implements Factory<EnergyMetrics> {
  private final SpironModule module;

  private final Provider<MetricsRegistry> registryProvider;

  public SpironModule_ProvideEnergyMetricsFactory(SpironModule module,
      Provider<MetricsRegistry> registryProvider) {
    this.module = module;
    this.registryProvider = registryProvider;
  }

  @Override
  public EnergyMetrics get() {
    return provideEnergyMetrics(module, registryProvider.get());
  }

  public static SpironModule_ProvideEnergyMetricsFactory create(SpironModule module,
      Provider<MetricsRegistry> registryProvider) {
    return new SpironModule_ProvideEnergyMetricsFactory(module, registryProvider);
  }

  public static EnergyMetrics provideEnergyMetrics(SpironModule instance,
      MetricsRegistry registry) {
    return Preconditions.checkNotNullFromProvides(instance.provideEnergyMetrics(registry));
  }
}
