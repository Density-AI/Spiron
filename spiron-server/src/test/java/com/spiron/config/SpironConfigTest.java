package com.spiron.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class SpironConfigTest {

  @Test
  void testConfigLoadDefaults() {
    SpironConfig cfg = SpironConfig.load();
    assertThat(cfg).isNotNull();
    assertThat(cfg.port()).isGreaterThan(0);
    // Peers can be empty in solo mode
    assertThat(cfg.peers()).isNotNull();
    assertThat(cfg.vectorDimensions()).isBetween(128, 2000);
    assertThat(cfg.profile()).isNotNull();
  }
}
