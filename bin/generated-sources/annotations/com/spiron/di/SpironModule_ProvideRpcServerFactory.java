package com.spiron.di;

import com.spiron.config.BroadcastValidationConfig;
import com.spiron.config.SpironConfig;
import com.spiron.core.EddyEngine;
import com.spiron.metrics.MetricsUpdater;
import com.spiron.metrics.RpcMetrics;
import com.spiron.metrics.StorageMetrics;
import com.spiron.network.RpcServer;
import com.spiron.serialization.CRDTJsonCodec;
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
public final class SpironModule_ProvideRpcServerFactory implements Factory<RpcServer> {
  private final SpironModule module;

  private final Provider<SpironConfig> cfgProvider;

  private final Provider<EddyEngine> engineProvider;

  private final Provider<CRDTStore> crdtStoreProvider;

  private final Provider<CRDTJsonCodec> codecProvider;

  private final Provider<RpcMetrics> rpcMetricsProvider;

  private final Provider<MetricsUpdater> metricsUpdaterProvider;

  private final Provider<StorageMetrics> storageMetricsProvider;

  private final Provider<BroadcastValidationConfig> validationConfigProvider;

  public SpironModule_ProvideRpcServerFactory(SpironModule module,
      Provider<SpironConfig> cfgProvider, Provider<EddyEngine> engineProvider,
      Provider<CRDTStore> crdtStoreProvider, Provider<CRDTJsonCodec> codecProvider,
      Provider<RpcMetrics> rpcMetricsProvider, Provider<MetricsUpdater> metricsUpdaterProvider,
      Provider<StorageMetrics> storageMetricsProvider,
      Provider<BroadcastValidationConfig> validationConfigProvider) {
    this.module = module;
    this.cfgProvider = cfgProvider;
    this.engineProvider = engineProvider;
    this.crdtStoreProvider = crdtStoreProvider;
    this.codecProvider = codecProvider;
    this.rpcMetricsProvider = rpcMetricsProvider;
    this.metricsUpdaterProvider = metricsUpdaterProvider;
    this.storageMetricsProvider = storageMetricsProvider;
    this.validationConfigProvider = validationConfigProvider;
  }

  @Override
  public RpcServer get() {
    return provideRpcServer(module, cfgProvider.get(), engineProvider.get(), crdtStoreProvider.get(), codecProvider.get(), rpcMetricsProvider.get(), metricsUpdaterProvider.get(), storageMetricsProvider.get(), validationConfigProvider.get());
  }

  public static SpironModule_ProvideRpcServerFactory create(SpironModule module,
      Provider<SpironConfig> cfgProvider, Provider<EddyEngine> engineProvider,
      Provider<CRDTStore> crdtStoreProvider, Provider<CRDTJsonCodec> codecProvider,
      Provider<RpcMetrics> rpcMetricsProvider, Provider<MetricsUpdater> metricsUpdaterProvider,
      Provider<StorageMetrics> storageMetricsProvider,
      Provider<BroadcastValidationConfig> validationConfigProvider) {
    return new SpironModule_ProvideRpcServerFactory(module, cfgProvider, engineProvider, crdtStoreProvider, codecProvider, rpcMetricsProvider, metricsUpdaterProvider, storageMetricsProvider, validationConfigProvider);
  }

  public static RpcServer provideRpcServer(SpironModule instance, SpironConfig cfg,
      EddyEngine engine, CRDTStore crdtStore, CRDTJsonCodec codec, RpcMetrics rpcMetrics,
      MetricsUpdater metricsUpdater, StorageMetrics storageMetrics,
      BroadcastValidationConfig validationConfig) {
    return Preconditions.checkNotNullFromProvides(instance.provideRpcServer(cfg, engine, crdtStore, codec, rpcMetrics, metricsUpdater, storageMetrics, validationConfig));
  }
}
