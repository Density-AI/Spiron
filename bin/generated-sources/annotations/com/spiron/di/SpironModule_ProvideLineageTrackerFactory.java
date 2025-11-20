package com.spiron.di;

import com.spiron.core.LineageTracker;
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
public final class SpironModule_ProvideLineageTrackerFactory implements Factory<LineageTracker> {
  private final SpironModule module;

  private final Provider<CRDTStore> crdtStoreProvider;

  public SpironModule_ProvideLineageTrackerFactory(SpironModule module,
      Provider<CRDTStore> crdtStoreProvider) {
    this.module = module;
    this.crdtStoreProvider = crdtStoreProvider;
  }

  @Override
  public LineageTracker get() {
    return provideLineageTracker(module, crdtStoreProvider.get());
  }

  public static SpironModule_ProvideLineageTrackerFactory create(SpironModule module,
      Provider<CRDTStore> crdtStoreProvider) {
    return new SpironModule_ProvideLineageTrackerFactory(module, crdtStoreProvider);
  }

  public static LineageTracker provideLineageTracker(SpironModule instance, CRDTStore crdtStore) {
    return Preconditions.checkNotNullFromProvides(instance.provideLineageTracker(crdtStore));
  }
}
