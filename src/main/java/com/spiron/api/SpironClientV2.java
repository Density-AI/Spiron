package com.spiron.api;

import com.spiron.core.EddyState;
import java.io.Closeable;
import java.util.Map;

/**
 * Backward-compatible facade for SpironClient V1 API users.
 *
 * <p><b>NOT related to Apache Ratis</b> - This is simply a wrapper to help users
 * migrate from an older SpironClient API. For Apache Ratis compatibility,
 * see {@link StateMachine} interface instead.</p>
 *
 * <p><b>Deprecation Notice:</b> This class will be removed in v2.0.0.
 * Please migrate to {@link SpironClient} directly.</p>
 *
 * <p><b>Migration Example:</b></p>
 * <pre>{@code
 * // Old (deprecated)
 * SpironClientV2 client = SpironClientV2.fromProperties(props);
 *
 * // New (recommended)
 * SpironClient client = SpironClient.fromProperties(props);
 * }</pre>
 *
 * @deprecated Use {@link SpironClient} directly. Scheduled for removal in v2.0.0.
 */
@Deprecated // DEAD CODE: Scheduled for removal in v2.0.0
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
