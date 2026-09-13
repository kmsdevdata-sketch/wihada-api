package io.point3.p3api.account.domain.value;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessRegistrationNumberTest {

  @Test
  @DisplayName("하이픈과 공백을 제거해 10자리 사업자등록번호로 정규화한다")
  void normalizesSeparators() {
    BusinessRegistrationNumber number = BusinessRegistrationNumber.of(" 123-45 67890 ");

    assertEquals("1234567890", number.value());
  }

  @Test
  @DisplayName("조회용 사업자등록번호는 뒤 네 자리만 노출한다")
  void masksBusinessRegistrationNumber() {
    BusinessRegistrationNumber number = BusinessRegistrationNumber.of("1234567890");

    assertEquals("******7890", number.masked());
  }

  @Test
  @DisplayName("10자리 숫자가 아니면 거부한다")
  void rejectsInvalidNumber() {
    assertThrows(IllegalArgumentException.class, () -> BusinessRegistrationNumber.of("123456789"));
    assertThrows(IllegalArgumentException.class, () -> BusinessRegistrationNumber.of("12345678901"));
    assertThrows(IllegalArgumentException.class, () -> BusinessRegistrationNumber.of("12345ABCDE"));
  }
}
