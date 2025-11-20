package com.spiron.validation;

import com.spiron.config.BroadcastValidationConfig;
import com.spiron.core.EddyState;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates broadcast messages for crash fault tolerance.
 * Checks vector dimensions, energy bounds, and ID format.
 */
public class BroadcastValidator {

  private static final Logger log = LoggerFactory.getLogger(BroadcastValidator.class);

  private final int expectedVectorDimensions;
  private final double minEnergy;
  private final double maxEnergy;
  private final Pattern idPattern;

  public BroadcastValidator(BroadcastValidationConfig config) {
    this.expectedVectorDimensions = config.vectorDimensions();
    this.minEnergy = config.minEnergy();
    this.maxEnergy = config.maxEnergy();
    this.idPattern = Pattern.compile(config.idPattern());
  }

  /**
   * Validates an EddyState for broadcast acceptance.
   * 
   * @param state the state to validate
   * @return ValidationResult with success/failure and reason
   */
  public ValidationResult validate(EddyState state) {
    // Validate ID format
    if (state.id() == null || state.id().isBlank()) {
      return ValidationResult.failure("ID_EMPTY", "Eddy ID cannot be null or empty");
    }
    if (!idPattern.matcher(state.id()).matches()) {
      return ValidationResult.failure("ID_INVALID_FORMAT", 
        "Eddy ID does not match required pattern: " + idPattern.pattern());
    }

    // Validate vector dimensions
    if (state.vector() == null) {
      return ValidationResult.failure("VECTOR_NULL", "Vector cannot be null");
    }
    if (state.vector().length != expectedVectorDimensions) {
      return ValidationResult.failure("VECTOR_DIMENSION_MISMATCH",
        String.format("Expected %d dimensions, got %d", 
          expectedVectorDimensions, state.vector().length));
    }

    // Validate vector values (no NaN, no Infinity)
    for (int i = 0; i < state.vector().length; i++) {
      double val = state.vector()[i];
      if (Double.isNaN(val) || Double.isInfinite(val)) {
        return ValidationResult.failure("VECTOR_INVALID_VALUE",
          String.format("Vector contains invalid value at index %d: %f", i, val));
      }
    }

    // Validate energy bounds
    if (Double.isNaN(state.energy()) || Double.isInfinite(state.energy())) {
      return ValidationResult.failure("ENERGY_INVALID_VALUE",
        "Energy is NaN or Infinite: " + state.energy());
    }
    if (state.energy() < minEnergy) {
      return ValidationResult.failure("ENERGY_TOO_LOW",
        String.format("Energy %.4f below minimum %.4f", state.energy(), minEnergy));
    }
    if (state.energy() > maxEnergy) {
      return ValidationResult.failure("ENERGY_TOO_HIGH",
        String.format("Energy %.4f exceeds maximum %.4f", state.energy(), maxEnergy));
    }

    return ValidationResult.success();
  }

  /**
   * Result of validation with success flag and optional error details.
   */
  public record ValidationResult(boolean valid, String errorCode, String errorMessage) {
    public static ValidationResult success() {
      return new ValidationResult(true, null, null);
    }

    public static ValidationResult failure(String errorCode, String errorMessage) {
      return new ValidationResult(false, errorCode, errorMessage);
    }

    public boolean isValid() {
      return valid;
    }
  }
}
