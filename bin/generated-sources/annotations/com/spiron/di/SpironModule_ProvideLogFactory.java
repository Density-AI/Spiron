package com.spiron.di;

import com.spiron.config.SpironConfig;
import com.spiron.core.SpironRaftLog;
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
public final class SpironModule_ProvideLogFactory implements Factory<SpironRaftLog> {
  private final SpironModule module;

  private final Provider<SpironConfig> cfgProvider;

  public SpironModule_ProvideLogFactory(SpironModule module, Provider<SpironConfig> cfgProvider) {
    this.module = module;
    this.cfgProvider = cfgProvider;
  }

  @Override
  public SpironRaftLog get() {
    return provideLog(module, cfgProvider.get());
  }

  public static SpironModule_ProvideLogFactory create(SpironModule module,
      Provider<SpironConfig> cfgProvider) {
    return new SpironModule_ProvideLogFactory(module, cfgProvider);
  }

  public static SpironRaftLog provideLog(SpironModule instance, SpironConfig cfg) {
    return Preconditions.checkNotNullFromProvides(instance.provideLog(cfg));
  }
}
