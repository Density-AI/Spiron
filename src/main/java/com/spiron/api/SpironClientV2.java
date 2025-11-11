package com.spiron.api;

import com.spiron.core.EddyState;
import java.io.Closeable;
import java.util.Map;

/**
 * Backward-compatible facade kept for a short transition period.
 * Delegates to the promoted {@link SpironClient} API.
 */
@Deprecated
public final class SpironClientV2 implements Closeable {

  private final SpironClient delegate;

  private SpironClientV2(SpironClient delegate) {
    this.delegate = delegate;
  }

  public static SpironClientV2 fromProperties(Map<String, String> props) {
    return new SpironClientV2(SpironClient.fromProperties(props));
  }

  public void propose(EddyState state) {
    try {
      delegate.propose(state);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void commit(EddyState state) {
    try {
      delegate.commit(state);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    delegate.close();
  }
}
