package com.spiron.di;

import com.spiron.metrics.MetricsRegistry;
import com.spiron.metrics.StorageMetrics;
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
public final class SpironModule_ProvideStorageMetricsFactory implements Factory<StorageMetrics> {
  private final SpironModule module;

  private final Provider<MetricsRegistry> registryProvider;

  public SpironModule_ProvideStorageMetricsFactory(SpironModule module,
      Provider<MetricsRegistry> registryProvider) {
    this.module = module;
    this.registryProvider = registryProvider;
  }

  @Override
  public StorageMetrics get() {
    return provideStorageMetrics(module, registryProvider.get());
  }

  public static SpironModule_ProvideStorageMetricsFactory create(SpironModule module,
      Provider<MetricsRegistry> registryProvider) {
    return new SpironModule_ProvideStorageMetricsFactory(module, registryProvider);
  }

  public static StorageMetrics provideStorageMetrics(SpironModule instance,
      MetricsRegistry registry) {
    return Preconditions.checkNotNullFromProvides(instance.provideStorageMetrics(registry));
  }
}
