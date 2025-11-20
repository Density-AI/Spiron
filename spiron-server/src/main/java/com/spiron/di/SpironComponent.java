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

/**
 * Dagger component providing dependency injection for Spiron consensus engine.
 * 
 * <p>This component manages the lifecycle and dependencies of core Spiron components
 * including the consensus engine, network layer, metrics, and configuration.</p>
 * 
 * <p>Usage:</p>
 * <pre>{@code
 * SpironConfig config = SpironConfig.load();
 * BlsSigner signer = new BlsSigner();
 * SpironComponent component = DaggerSpironComponent.builder()
 *     .config(config)
 *     .blsSigner(signer)
 *     .build();
 * }</pre>
 */
@Singleton
@Component(modules = SpironModule.class)
public interface SpironComponent {
  /** Returns the Spiron configuration */
  SpironConfig config();
  
  /** Returns the Eddy consensus engine */
  EddyEngine engine();
  
  /** Returns the RPC server for accepting connections */
  RpcServer rpcServer();
  
  /** Returns the RPC client for making connections */
  RpcClient rpcClient();
  
  /** Returns the metrics registry */
  MetricsRegistry metricsRegistry();
  
  /** Returns the energy metrics collector */
  EnergyMetrics energyMetrics();

  /**
   * Builder for creating SpironComponent instances.
   */
  @Component.Builder
  interface Builder {
    /**
     * Sets the Spiron configuration.
     * @param config the configuration instance
     * @return this builder
     */
    @BindsInstance
    Builder config(SpironConfig config);

    /**
     * Sets the BLS signer for cryptographic operations.
     * @param signer the BLS signer instance
     * @return this builder
     */
    @BindsInstance
    Builder blsSigner(BlsSigner signer);

    /**
     * Builds the component.
     * @return the configured SpironComponent
     */
    SpironComponent build();
  }
}
