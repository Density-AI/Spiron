package com.spiron.di;

import com.spiron.config.BroadcastValidationConfig;
import com.spiron.config.SpironConfig;
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
public final class SpironModule_ProvideBroadcastValidationConfigFactory implements Factory<BroadcastValidationConfig> {
  private final SpironModule module;

  private final Provider<SpironConfig> cfgProvider;

  public SpironModule_ProvideBroadcastValidationConfigFactory(SpironModule module,
      Provider<SpironConfig> cfgProvider) {
    this.module = module;
    this.cfgProvider = cfgProvider;
  }

  @Override
  public BroadcastValidationConfig get() {
    return provideBroadcastValidationConfig(module, cfgProvider.get());
  }

  public static SpironModule_ProvideBroadcastValidationConfigFactory create(SpironModule module,
      Provider<SpironConfig> cfgProvider) {
    return new SpironModule_ProvideBroadcastValidationConfigFactory(module, cfgProvider);
  }

  public static BroadcastValidationConfig provideBroadcastValidationConfig(SpironModule instance,
      SpironConfig cfg) {
    return Preconditions.checkNotNullFromProvides(instance.provideBroadcastValidationConfig(cfg));
  }
}
