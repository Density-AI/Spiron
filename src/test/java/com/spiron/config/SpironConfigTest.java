package com.spiron.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class SpironConfigTest {

  @Test
  void testConfigLoadDefaults() {
    SpironConfig cfg = SpironConfig.load();
    assertThat(cfg).isNotNull();
    assertThat(cfg.port()).isGreaterThan(0);
    assertThat(cfg.peers()).isNotEmpty();
  }
}
