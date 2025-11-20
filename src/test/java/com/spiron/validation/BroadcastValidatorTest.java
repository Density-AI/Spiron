package com.spiron.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.spiron.config.BroadcastValidationConfig;
import com.spiron.core.EddyState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BroadcastValidatorTest {

  private BroadcastValidator validator;
  private static final int VECTOR_DIM = 128;

  @BeforeEach
  void setUp() {
    var config = new BroadcastValidationConfig(
      VECTOR_DIM,      // vectorDimensions
      0.0,             // minEnergy
      100.0,           // maxEnergy
      "^[a-zA-Z0-9_-]{1,128}$",  // idPattern
      60000,           // duplicateExpiryMs
      100,             // rateLimitPerSecond
      ""               // peerAllowlistRegex (disabled)
    );
    validator = new BroadcastValidator(config);
  }

  @Test
  void testValidState() {
    double[] vec = new double[VECTOR_DIM];
    for (int i = 0; i < VECTOR_DIM; i++) {
      vec[i] = Math.random();
    }
    var state = new EddyState("test-eddy-123", vec, 50.0, null);
    
    var result = validator.validate(state);
    assertTrue(result.isValid(), "Valid state should pass validation");
  }

  @Test
  void testEmptyId() {
    double[] vec = new double[VECTOR_DIM];
    var state = new EddyState("", vec, 50.0, null);
    
    var result = validator.validate(state);
    assertFalse(result.isValid());
    assertEquals("ID_EMPTY", result.errorCode());
  }

  @Test
  void testInvalidIdPattern() {
    double[] vec = new double[VECTOR_DIM];
    var state = new EddyState("invalid!@#$%", vec, 50.0, null);
    
    var result = validator.validate(state);
    assertFalse(result.isValid());
    assertEquals("ID_INVALID_FORMAT", result.errorCode());
  }

  @Test
  void testNullVector() {
    var state = new EddyState("test-eddy", null, 50.0, null);
    
    var result = validator.validate(state);
    assertFalse(result.isValid());
    assertEquals("VECTOR_NULL", result.errorCode());
  }

  @Test
  void testWrongVectorDimension() {
    double[] vec = new double[64]; // Wrong size
    var state = new EddyState("test-eddy", vec, 50.0, null);
    
    var result = validator.validate(state);
    assertFalse(result.isValid());
    assertEquals("VECTOR_DIMENSION_MISMATCH", result.errorCode());
  }

  @Test
  void testVectorContainsNaN() {
    double[] vec = new double[VECTOR_DIM];
    vec[0] = Double.NaN;
    var state = new EddyState("test-eddy", vec, 50.0, null);
    
    var result = validator.validate(state);
    assertFalse(result.isValid());
    assertEquals("VECTOR_INVALID_VALUE", result.errorCode());
  }

  @Test
  void testVectorContainsInfinity() {
    double[] vec = new double[VECTOR_DIM];
    vec[10] = Double.POSITIVE_INFINITY;
    var state = new EddyState("test-eddy", vec, 50.0, null);
    
    var result = validator.validate(state);
    assertFalse(result.isValid());
    assertEquals("VECTOR_INVALID_VALUE", result.errorCode());
  }

  @Test
  void testEnergyTooLow() {
    double[] vec = new double[VECTOR_DIM];
    var state = new EddyState("test-eddy", vec, -1.0, null);
    
    var result = validator.validate(state);
    assertFalse(result.isValid());
    assertEquals("ENERGY_TOO_LOW", result.errorCode());
  }

  @Test
  void testEnergyTooHigh() {
    double[] vec = new double[VECTOR_DIM];
    var state = new EddyState("test-eddy", vec, 150.0, null);
    
    var result = validator.validate(state);
    assertFalse(result.isValid());
    assertEquals("ENERGY_TOO_HIGH", result.errorCode());
  }

  @Test
  void testEnergyIsNaN() {
    double[] vec = new double[VECTOR_DIM];
    var state = new EddyState("test-eddy", vec, Double.NaN, null);
    
    var result = validator.validate(state);
    assertFalse(result.isValid());
    assertEquals("ENERGY_INVALID_VALUE", result.errorCode());
  }

  @Test
  void testBoundaryValues() {
    double[] vec = new double[VECTOR_DIM];
    
    // Test minimum energy boundary
    var state1 = new EddyState("test-eddy", vec, 0.0, null);
    assertTrue(validator.validate(state1).isValid());
    
    // Test maximum energy boundary
    var state2 = new EddyState("test-eddy", vec, 100.0, null);
    assertTrue(validator.validate(state2).isValid());
  }
}
