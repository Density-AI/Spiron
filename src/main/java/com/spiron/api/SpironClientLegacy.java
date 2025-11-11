package com.spiron.api;

import com.spiron.core.EddyState;
import com.spiron.network.RpcClient;

/**
 * Legacy compatibility wrapper that preserves the old constructor signature
 * SpironClient(RpcClient). New code should use {@link SpironClient} instead.
 */
@Deprecated
public class SpironClientLegacy {

  private final RpcClient rpcClient;
  private final SpironClient delegate;

  public SpironClientLegacy(RpcClient rpcClient) {
    this.rpcClient = rpcClient;
    this.delegate = null;
  }

  private SpironClientLegacy(SpironClient delegate) {
    this.rpcClient = null;
    this.delegate = delegate;
  }

  public static SpironClientLegacy fromProperties(
    java.util.Map<String, String> props
  ) {
    var client = SpironClient.fromProperties(props);
    return new SpironClientLegacy(client);
  }

  public void propose(EddyState state) {
    if (delegate != null) {
      try {
        delegate.propose(state);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return;
    }
    rpcClient.broadcast(state);
  }

  public void commit(EddyState state) {
    if (delegate != null) {
      try {
        delegate.commit(state);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return;
    }
    rpcClient.commit(state);
  }
}
