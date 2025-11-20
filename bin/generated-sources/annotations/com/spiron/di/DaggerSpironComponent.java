package com.spiron.di;

import com.spiron.config.BroadcastValidationConfig;
import com.spiron.config.SpironConfig;
import com.spiron.core.EddyEngine;
import com.spiron.core.LineageTracker;
import com.spiron.core.SpironRaftLog;
import com.spiron.core.SpironSnapshotStore;
import com.spiron.metrics.EnergyMetrics;
import com.spiron.metrics.MetricsRegistry;
import com.spiron.metrics.MetricsUpdater;
import com.spiron.metrics.RpcMetrics;
import com.spiron.metrics.StorageMetrics;
import com.spiron.metrics.ThroughputMetrics;
import com.spiron.network.RpcClient;
import com.spiron.network.RpcServer;
import com.spiron.security.BlsSigner;
import com.spiron.serialization.CRDTJsonCodec;
import com.spiron.storage.CRDTStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.InstanceFactory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import javax.annotation.processing.Generated;

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
public final class DaggerSpironComponent {
  private DaggerSpironComponent() {
  }

  public static SpironComponent.Builder builder() {
    return new Builder();
  }

  private static final class Builder implements SpironComponent.Builder {
    private SpironConfig config;

    private BlsSigner blsSigner;

    @Override
    public Builder config(SpironConfig config) {
      this.config = Preconditions.checkNotNull(config);
      return this;
    }

    @Override
    public Builder blsSigner(BlsSigner signer) {
      this.blsSigner = Preconditions.checkNotNull(signer);
      return this;
    }

    @Override
    public SpironComponent build() {
      Preconditions.checkBuilderRequirement(config, SpironConfig.class);
      Preconditions.checkBuilderRequirement(blsSigner, BlsSigner.class);
      return new SpironComponentImpl(new SpironModule(), config, blsSigner);
    }
  }

  private static final class SpironComponentImpl implements SpironComponent {
    private final SpironConfig config;

    private final SpironComponentImpl spironComponentImpl = this;

    private Provider<SpironConfig> configProvider;

    private Provider<SpironRaftLog> provideLogProvider;

    private Provider<SpironSnapshotStore> provideSnapshotsProvider;

    private Provider<BlsSigner> blsSignerProvider;

    private Provider<MetricsRegistry> provideMetricsRegistryProvider;

    private Provider<RpcMetrics> provideRpcMetricsProvider;

    private Provider<ThroughputMetrics> provideThroughputMetricsProvider;

    private Provider<RpcClient> provideRpcClientProvider;

    private Provider<EnergyMetrics> provideEnergyMetricsProvider;

    private Provider<StorageMetrics> provideStorageMetricsProvider;

    private Provider<CRDTStore> provideCRDTStoreProvider;

    private Provider<LineageTracker> provideLineageTrackerProvider;

    private Provider<EddyEngine> provideEngineProvider;

    private Provider<CRDTJsonCodec> provideCRDTJsonCodecProvider;

    private Provider<MetricsUpdater> provideMetricsUpdaterProvider;

    private Provider<BroadcastValidationConfig> provideBroadcastValidationConfigProvider;

    private Provider<RpcServer> provideRpcServerProvider;

    private SpironComponentImpl(SpironModule spironModuleParam, SpironConfig configParam,
        BlsSigner blsSignerParam) {
      this.config = configParam;
      initialize(spironModuleParam, configParam, blsSignerParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SpironModule spironModuleParam, final SpironConfig configParam,
        final BlsSigner blsSignerParam) {
      this.configProvider = InstanceFactory.create(configParam);
      this.provideLogProvider = DoubleCheck.provider(SpironModule_ProvideLogFactory.create(spironModuleParam, configProvider));
      this.provideSnapshotsProvider = DoubleCheck.provider(SpironModule_ProvideSnapshotsFactory.create(spironModuleParam, configProvider));
      this.blsSignerProvider = InstanceFactory.create(blsSignerParam);
      this.provideMetricsRegistryProvider = DoubleCheck.provider(SpironModule_ProvideMetricsRegistryFactory.create(spironModuleParam, configProvider));
      this.provideRpcMetricsProvider = DoubleCheck.provider(SpironModule_ProvideRpcMetricsFactory.create(spironModuleParam, provideMetricsRegistryProvider));
      this.provideThroughputMetricsProvider = DoubleCheck.provider(SpironModule_ProvideThroughputMetricsFactory.create(spironModuleParam, provideMetricsRegistryProvider));
      this.provideRpcClientProvider = DoubleCheck.provider(SpironModule_ProvideRpcClientFactory.create(spironModuleParam, configProvider, blsSignerProvider, provideRpcMetricsProvider, provideThroughputMetricsProvider));
      this.provideEnergyMetricsProvider = DoubleCheck.provider(SpironModule_ProvideEnergyMetricsFactory.create(spironModuleParam, provideMetricsRegistryProvider));
      this.provideStorageMetricsProvider = DoubleCheck.provider(SpironModule_ProvideStorageMetricsFactory.create(spironModuleParam, provideMetricsRegistryProvider));
      this.provideCRDTStoreProvider = DoubleCheck.provider(SpironModule_ProvideCRDTStoreFactory.create(spironModuleParam, configProvider));
      this.provideLineageTrackerProvider = DoubleCheck.provider(SpironModule_ProvideLineageTrackerFactory.create(spironModuleParam, provideCRDTStoreProvider));
      this.provideEngineProvider = DoubleCheck.provider(SpironModule_ProvideEngineFactory.create(spironModuleParam, configProvider, provideLogProvider, provideSnapshotsProvider, provideRpcClientProvider, provideEnergyMetricsProvider, provideThroughputMetricsProvider, provideStorageMetricsProvider, provideLineageTrackerProvider));
      this.provideCRDTJsonCodecProvider = DoubleCheck.provider(SpironModule_ProvideCRDTJsonCodecFactory.create(spironModuleParam));
      this.provideMetricsUpdaterProvider = DoubleCheck.provider(SpironModule_ProvideMetricsUpdaterFactory.create(spironModuleParam, provideStorageMetricsProvider, provideThroughputMetricsProvider, provideCRDTStoreProvider));
      this.provideBroadcastValidationConfigProvider = DoubleCheck.provider(SpironModule_ProvideBroadcastValidationConfigFactory.create(spironModuleParam, configProvider));
      this.provideRpcServerProvider = DoubleCheck.provider(SpironModule_ProvideRpcServerFactory.create(spironModuleParam, configProvider, provideEngineProvider, provideCRDTStoreProvider, provideCRDTJsonCodecProvider, provideRpcMetricsProvider, provideMetricsUpdaterProvider, provideStorageMetricsProvider, provideBroadcastValidationConfigProvider));
    }

    @Override
    public SpironConfig config() {
      return config;
    }

    @Override
    public EddyEngine engine() {
      return provideEngineProvider.get();
    }

    @Override
    public RpcServer rpcServer() {
      return provideRpcServerProvider.get();
    }

    @Override
    public RpcClient rpcClient() {
      return provideRpcClientProvider.get();
    }

    @Override
    public MetricsRegistry metricsRegistry() {
      return provideMetricsRegistryProvider.get();
    }

    @Override
    public EnergyMetrics energyMetrics() {
      return provideEnergyMetricsProvider.get();
    }
  }
}
