package com.spiron.core;

import static java.lang.Math.*;

/** Physics helpers for eddy dominance calculation. */
public final class EddyMath {

  private EddyMath() {}

  /** Angular similarity between two vectors (-1..1). */
  public static double angularSimilarity(double[] a, double[] b) {
    double dot = 0, n1 = 0, n2 = 0;
    for (int i = 0; i < a.length; i++) {
      dot += a[i] * b[i];
      n1 += a[i] * a[i];
      n2 += b[i] * b[i];
    }
    return dot / (sqrt(n1) * sqrt(n2) + 1e-9);
  }

  /** Energy siphon: dominant absorbs part of weak’s energy scaled by similarity. */
  public static double siphon(
    double dominant,
    double weak,
    double similarity,
    double factor
  ) {
    return dominant + factor * similarity * weak;
  }
}
