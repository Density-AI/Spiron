package com.spiron.di;

import com.spiron.config.SpironConfig;
import com.spiron.core.SpironSnapshotStore;
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
public final class SpironModule_ProvideSnapshotsFactory implements Factory<SpironSnapshotStore> {
  private final SpironModule module;

  private final Provider<SpironConfig> cfgProvider;

  public SpironModule_ProvideSnapshotsFactory(SpironModule module,
      Provider<SpironConfig> cfgProvider) {
    this.module = module;
    this.cfgProvider = cfgProvider;
  }

  @Override
  public SpironSnapshotStore get() {
    return provideSnapshots(module, cfgProvider.get());
  }

  public static SpironModule_ProvideSnapshotsFactory create(SpironModule module,
      Provider<SpironConfig> cfgProvider) {
    return new SpironModule_ProvideSnapshotsFactory(module, cfgProvider);
  }

  public static SpironSnapshotStore provideSnapshots(SpironModule instance, SpironConfig cfg) {
    return Preconditions.checkNotNullFromProvides(instance.provideSnapshots(cfg));
  }
}
