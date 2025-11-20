package com.spiron.di;

import com.spiron.config.SpironConfig;
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
public final class SpironModule_ProvideCRDTStoreFactory implements Factory<CRDTStore> {
  private final SpironModule module;

  private final Provider<SpironConfig> cfgProvider;

  public SpironModule_ProvideCRDTStoreFactory(SpironModule module,
      Provider<SpironConfig> cfgProvider) {
    this.module = module;
    this.cfgProvider = cfgProvider;
  }

  @Override
  public CRDTStore get() {
    return provideCRDTStore(module, cfgProvider.get());
  }

  public static SpironModule_ProvideCRDTStoreFactory create(SpironModule module,
      Provider<SpironConfig> cfgProvider) {
    return new SpironModule_ProvideCRDTStoreFactory(module, cfgProvider);
  }

  public static CRDTStore provideCRDTStore(SpironModule instance, SpironConfig cfg) {
    return Preconditions.checkNotNullFromProvides(instance.provideCRDTStore(cfg));
  }
}
