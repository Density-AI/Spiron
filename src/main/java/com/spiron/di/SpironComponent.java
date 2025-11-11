package com.spiron.di;

import com.spiron.config.SpironConfig;
import com.spiron.core.EddyEngine;
import com.spiron.metrics.EnergyMetrics;
import com.spiron.metrics.MetricsRegistry;
import com.spiron.network.RpcClient;
import com.spiron.network.RpcServer;
import com.spiron.security.BlsSigner;
import dagger.BindsInstance;
import dagger.Component;
import javax.inject.Singleton;

@Singleton
@Component(modules = SpironModule.class)
public interface SpironComponent {
  SpironConfig config();
  EddyEngine engine();
  RpcServer rpcServer();
  RpcClient rpcClient();
  MetricsRegistry metricsRegistry();
  EnergyMetrics energyMetrics();

  @Component.Builder
  interface Builder {
    @BindsInstance
    Builder config(SpironConfig config);

    @BindsInstance
    Builder blsSigner(BlsSigner signer);

    SpironComponent build();
  }
}
