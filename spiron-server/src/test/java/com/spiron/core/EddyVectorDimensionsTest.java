package com.spiron.core;

import static org.junit.jupiter.api.Assertions.*;

import com.spiron.security.BlsSigner;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for configurable dimensional vectors (128-2000 dimensions).
 */
class EddyVectorDimensionsTest {

  @Test
  void testMinimumDimensions_128() {
    int dimensions = 128;
    EddyState eddy = createRandomEddy(dimensions);
    
    assertEquals(dimensions, eddy.vector().length);
    assertTrue(eddy.energy() > 0);
    assertNotNull(eddy.id());
  }

  @Test
  void testMediumDimensions_512() {
    int dimensions = 512;
    EddyState eddy = createRandomEddy(dimensions);
    
    assertEquals(dimensions, eddy.vector().length);
    double magnitude = calculateMagnitude(eddy.vector());
    assertEquals(1.0, magnitude, 0.001, "Vector should be normalized");
  }

  @Test
  void testLargeDimensions_1024() {
    int dimensions = 1024;
    EddyState eddy = createRandomEddy(dimensions);
    
    assertEquals(dimensions, eddy.vector().length);
    double magnitude = calculateMagnitude(eddy.vector());
    assertEquals(1.0, magnitude, 0.001, "Vector should be normalized");
  }

  @Test
  void testMaximumDimensions_2000() {
    int dimensions = 2000;
    EddyState eddy = createRandomEddy(dimensions);
    
    assertEquals(dimensions, eddy.vector().length);
    double magnitude = calculateMagnitude(eddy.vector());
    assertEquals(1.0, magnitude, 0.001, "Vector should be normalized");
  }

  @Test
  void testAngularSimilarity_SameDimensions() {
    int dimensions = 256;
    EddyState eddy1 = createRandomEddy(dimensions);
    EddyState eddy2 = createRandomEddy(dimensions);
    
    double similarity = EddyMath.angularSimilarity(eddy1.vector(), eddy2.vector());
    
    assertTrue(similarity >= -1.0 && similarity <= 1.0, 
      "Angular similarity must be between -1 and 1");
  }

  @Test
  void testAngularSimilarity_IdenticalVectors() {
    int dimensions = 256;
    double[] vector = createNormalizedRandomVector(dimensions);
    
    EddyState eddy1 = new EddyState("eddy-1", vector, 1.0, null);
    EddyState eddy2 = new EddyState("eddy-2", vector, 1.0, null);
    
    double similarity = EddyMath.angularSimilarity(eddy1.vector(), eddy2.vector());
    
    assertEquals(1.0, similarity, 0.001, "Identical vectors should have similarity 1.0");
  }

  @Test
  void testAngularSimilarity_OrthogonalVectors() {
    int dimensions = 128;
    double[] vector1 = new double[dimensions];
    double[] vector2 = new double[dimensions];
    
    // Create orthogonal vectors
    vector1[0] = 1.0;
    vector2[1] = 1.0;
    
    EddyState eddy1 = new EddyState("eddy-1", vector1, 1.0, null);
    EddyState eddy2 = new EddyState("eddy-2", vector2, 1.0, null);
    
    double similarity = EddyMath.angularSimilarity(eddy1.vector(), eddy2.vector());
    
    assertEquals(0.0, similarity, 0.001, "Orthogonal vectors should have similarity 0.0");
  }

  @Test
  void testEnergySiphon_HighSimilarity() {
    double dominant = 1.0;
    double weak = 0.5;
    double similarity = 0.9;
    double factor = 0.45;
    
    double result = EddyMath.siphon(dominant, weak, similarity, factor);
    
    assertTrue(result > dominant, "Dominant energy should increase");
    assertTrue(result < dominant + weak, "Energy should not exceed sum");
  }

  @Test
  void testEnergySiphon_LowSimilarity() {
    double dominant = 1.0;
    double weak = 0.5;
    double similarity = 0.1;
    double factor = 0.45;
    
    double result = EddyMath.siphon(dominant, weak, similarity, factor);
    
    assertTrue(result > dominant, "Energy should still increase slightly");
    assertTrue(result < 1.1, "Low similarity should limit energy gain");
  }

  @Test
  void testBLSSignature_VariousDimensions() {
    BlsSigner signer = new BlsSigner();
    
    for (int dimensions : new int[]{128, 256, 512, 1024, 2000}) {
      EddyState eddy = createRandomEddy(dimensions);
      byte[] message = (eddy.id() + eddy.energy()).getBytes();
      byte[] signature = signer.sign(message);
      
      boolean verified = BlsSigner.verifyAggregate(
        java.util.List.of(signer.publicKey()),
        java.util.List.of(message),
        signature
      );
      
      assertTrue(verified, "Signature should verify for " + dimensions + "D vector");
    }
  }

  @Test
  void testEddyEngine_MultipleDimensions() {
    for (int dimensions : new int[]{128, 256, 512}) {
      EddyEngine engine = new EddyEngine(0.85, 0.45, 0.6, 1.0);
      
      for (int i = 0; i < 10; i++) {
        EddyState eddy = createRandomEddy(dimensions);
        engine.ingest(eddy);
      }
      
      var snapshot = engine.snapshot();
      assertEquals(10, snapshot.size(), "Should have 10 eddys for " + dimensions + "D");
    }
  }

  @Test
  void testEddyEngine_Convergence() {
    int dimensions = 256;
    EddyEngine engine = new EddyEngine(0.85, 0.45, 0.6, 1.0);
    
    // Create similar eddys
    double[] baseVector = createNormalizedRandomVector(dimensions);
    for (int i = 0; i < 5; i++) {
      double[] similarVector = addNoise(baseVector, 0.1);
      EddyState eddy = new EddyState("eddy-" + i, similarVector, 1.0, null);
      engine.ingest(eddy);
    }
    
    var dominant = engine.dominant();
    assertTrue(dominant.isPresent(), "Should have a dominant eddy");
    assertTrue(dominant.get().energy() >= 1.0, "Dominant energy should be significant");
  }

  @Test
  void testPerformance_HighDimensionalVectors() {
    int dimensions = 2000;
    int count = 1000;
    
    long startTime = System.nanoTime();
    
    for (int i = 0; i < count; i++) {
      EddyState eddy = createRandomEddy(dimensions);
      assertNotNull(eddy);
    }
    
    long endTime = System.nanoTime();
    double durationMs = (endTime - startTime) / 1_000_000.0;
    
    System.out.println("Created " + count + " " + dimensions + "D eddys in " + 
      String.format("%.2f ms", durationMs));
    assertTrue(durationMs < 5000, "Should create 1000 2000D eddys in under 5 seconds");
  }

  private EddyState createRandomEddy(int dimensions) {
    double[] vector = createNormalizedRandomVector(dimensions);
    String id = "eddy-" + System.nanoTime();
    double energy = 0.5 + Math.random() * 0.5;
    return new EddyState(id, vector, energy, null);
  }

  private double[] createNormalizedRandomVector(int dimensions) {
    java.util.Random random = new java.util.Random();
    double[] vector = new double[dimensions];
    double magnitude = 0;
    
    for (int i = 0; i < dimensions; i++) {
      vector[i] = random.nextGaussian();
      magnitude += vector[i] * vector[i];
    }
    
    magnitude = Math.sqrt(magnitude);
    for (int i = 0; i < dimensions; i++) {
      vector[i] /= magnitude;
    }
    
    return vector;
  }

  private double[] addNoise(double[] vector, double noiseLevel) {
    java.util.Random random = new java.util.Random();
    double[] noisy = new double[vector.length];
    double magnitude = 0;
    
    for (int i = 0; i < vector.length; i++) {
      noisy[i] = vector[i] + random.nextGaussian() * noiseLevel;
      magnitude += noisy[i] * noisy[i];
    }
    
    magnitude = Math.sqrt(magnitude);
    for (int i = 0; i < noisy.length; i++) {
      noisy[i] /= magnitude;
    }
    
    return noisy;
  }

  private double calculateMagnitude(double[] vector) {
    double sum = 0;
    for (double v : vector) {
      sum += v * v;
    }
    return Math.sqrt(sum);
  }
}
