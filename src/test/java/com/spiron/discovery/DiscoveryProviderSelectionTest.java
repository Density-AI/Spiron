package com.spiron.discovery;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class DiscoveryProviderSelectionTest {

  @AfterEach
  void cleanup() {
    Discovery.reset();
  }

  @Test
  void selectsRegisteredProviderFromProperties() {
    // register a test provider
    Discovery.registerProvider("testprov", (h, p) -> List.of("10.10.10.1:" + p)
    );

    Properties props = new Properties();
    props.setProperty("spiron.discovery.provider", "testprov");
    Discovery.initFrom(props);

    var resolved = Discovery.resolve("svc", 9001);
    assertTrue(resolved.contains("10.10.10.1:9001"));
  }
}
