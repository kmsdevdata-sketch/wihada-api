package io.point3.p3api.account.domain.value;

import java.util.Objects;

public final class BusinessRegistrationNumber {

  private static final int LENGTH = 10;
  private static final int VISIBLE_SUFFIX_LENGTH = 4;

  private final String value;

  private BusinessRegistrationNumber(String value) {
    this.value = normalize(value);
    validate(this.value);
  }

  public static BusinessRegistrationNumber of(String value) {
    return new BusinessRegistrationNumber(value);
  }

  public String value() {
    return value;
  }

  public String masked() {
    int maskedLength = LENGTH - VISIBLE_SUFFIX_LENGTH;
    return "*".repeat(maskedLength) + value.substring(maskedLength);
  }

  @Override
  public String toString() {
    return "BusinessRegistrationNumber[masked=" + masked() + "]";
  }

  private static String normalize(String value) {
    Objects.requireNonNull(value, "value");
    return value.replace("-", "").replaceAll("\\s+", "");
  }

  private static void validate(String value) {
    if (!value.matches("[0-9]{" + LENGTH + "}")) {
      throw new IllegalArgumentException("Business registration number must contain 10 digits");
    }
  }
}
