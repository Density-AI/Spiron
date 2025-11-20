package com.spiron.di;

import com.spiron.config.SpironConfig;
import com.spiron.metrics.RpcMetrics;
import com.spiron.metrics.ThroughputMetrics;
import com.spiron.network.RpcClient;
import com.spiron.security.BlsSigner;
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
public final class SpironModule_ProvideRpcClientFactory implements Factory<RpcClient> {
  private final SpironModule module;

  private final Provider<SpironConfig> cfgProvider;

  private final Provider<BlsSigner> signerProvider;

  private final Provider<RpcMetrics> rpcMetricsProvider;

  private final Provider<ThroughputMetrics> throughputMetricsProvider;

  public SpironModule_ProvideRpcClientFactory(SpironModule module,
      Provider<SpironConfig> cfgProvider, Provider<BlsSigner> signerProvider,
      Provider<RpcMetrics> rpcMetricsProvider,
      Provider<ThroughputMetrics> throughputMetricsProvider) {
    this.module = module;
    this.cfgProvider = cfgProvider;
    this.signerProvider = signerProvider;
    this.rpcMetricsProvider = rpcMetricsProvider;
    this.throughputMetricsProvider = throughputMetricsProvider;
  }

  @Override
  public RpcClient get() {
    return provideRpcClient(module, cfgProvider.get(), signerProvider.get(), rpcMetricsProvider.get(), throughputMetricsProvider.get());
  }

  public static SpironModule_ProvideRpcClientFactory create(SpironModule module,
      Provider<SpironConfig> cfgProvider, Provider<BlsSigner> signerProvider,
      Provider<RpcMetrics> rpcMetricsProvider,
      Provider<ThroughputMetrics> throughputMetricsProvider) {
    return new SpironModule_ProvideRpcClientFactory(module, cfgProvider, signerProvider, rpcMetricsProvider, throughputMetricsProvider);
  }

  public static RpcClient provideRpcClient(SpironModule instance, SpironConfig cfg,
      BlsSigner signer, RpcMetrics rpcMetrics, ThroughputMetrics throughputMetrics) {
    return Preconditions.checkNotNullFromProvides(instance.provideRpcClient(cfg, signer, rpcMetrics, throughputMetrics));
  }
}
