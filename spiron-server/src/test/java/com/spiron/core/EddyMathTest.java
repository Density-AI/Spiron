package com.spiron.core;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class EddyMathTest {

  @Test
  void testAngularSimilarity() {
    double[] v1 = { 1, 0 };
    double[] v2 = { 0, 1 };
    double[] v3 = { 1, 1 };

    assertThat(EddyMath.angularSimilarity(v1, v2)).isCloseTo(
      0.0,
      within(0.001)
    );
    assertThat(EddyMath.angularSimilarity(v1, v3)).isCloseTo(
      0.707,
      within(0.01)
    );
  }

  @Test
  void testSiphonIncreasesDominantEnergy() {
    double dominant = 10.0;
    double weak = 4.0;
    double sim = 0.9;
    double factor = 0.5;
    double result = EddyMath.siphon(dominant, weak, sim, factor);
    assertThat(result).isGreaterThan(dominant);
    assertThat(result).isCloseTo(10 + 0.5 * 0.9 * 4, within(0.0001));
  }
}
