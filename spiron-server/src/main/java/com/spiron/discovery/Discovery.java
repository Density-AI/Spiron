package com.spiron.discovery;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Small pluggable discovery helper used by SpironConfig to expand tokens.
 *
 * Default resolver uses InetAddress.getAllByName. Tests can override
 * the resolver via {@link #setResolver(BiFunction)} to provide deterministic
 * values.
 */
public final class Discovery {

  // Provider map (name -> resolver)
  private static final java.util.concurrent.ConcurrentHashMap<
    String,
    BiFunction<String, Integer, List<String>>
  > providers = new java.util.concurrent.ConcurrentHashMap<>();

  // Active provider resolver (defaults to dns)
  private static volatile BiFunction<
    String,
    Integer,
    List<String>
  > activeResolver;

  static {
    // register default 'dns' provider
    BiFunction<String, Integer, List<String>> dns = (host, port) -> {
      try {
        var addrs = java.net.InetAddress.getAllByName(host);
        java.util.List<String> out = new java.util.ArrayList<>();
        for (var a : addrs) {
          String h = a.getHostAddress();
          if (port > 0) out.add(h + ":" + port);
          else out.add(h);
        }
        return out;
      } catch (Exception e) {
        return java.util.List.of(host);
      }
    };
    providers.put("dns", dns);
    providers.put("default", dns);
    activeResolver = dns;
  }

  /**
   * Resolve a host via the active discovery provider.
   */
  public static List<String> resolve(String host, int port) {
    return activeResolver.apply(host, port);
  }

  /** Register a named provider resolver. */
  public static void registerProvider(
    String name,
    BiFunction<String, Integer, List<String>> resolver
  ) {
    providers.put(name, resolver);
  }

  /** Select the active provider by name if available. */
  public static void setProvider(String name) {
    var r = providers.get(name);
    if (r != null) activeResolver = r;
  }

  /** Initialize discovery from properties – uses 'spiron.discovery.provider'. */
  public static void initFrom(java.util.Properties props) {
    if (props == null) return;
    String p = props.getProperty("spiron.discovery.provider");
    if (p != null && !p.isBlank()) setProvider(p.trim());
    // Also allow environment override (SPRION_DISCOVERY_PROVIDER or spiron_DISCOVERY_PROVIDER)
    String env = System.getenv()
      .getOrDefault(
        "spiron_DISCOVERY_PROVIDER",
        System.getenv().getOrDefault("SPIRON_DISCOVERY_PROVIDER", "")
      );
    if (env != null && !env.isBlank()) setProvider(env.trim());
  }

  /** Allows tests to replace the resolver directly. Backwards-compatible with earlier API. */
  public static void setResolver(BiFunction<String, Integer, List<String>> r) {
    activeResolver = r;
  }

  /** Reset to built-in defaults. */
  public static void reset() {
    activeResolver = providers.getOrDefault("default", activeResolver);
  }
}
