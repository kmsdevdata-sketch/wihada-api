package io.point3.p3api.account.application.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.point3.p3api.account.application.port.AccountRealNameVerificationPort;
import io.point3.p3api.account.application.port.SensitiveDataCipher;
import io.point3.p3api.account.application.settlement.port.SellerSettlementAccountPersistencePort;
import io.point3.p3api.account.domain.entity.SellerSettlementAccount;
import io.point3.p3api.account.domain.type.AccountHolderType;
import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.AccountErrorCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SellerSettlementAccountServiceTest {

  private final SellerSettlementAccountPersistencePort persistencePort =
      mock(SellerSettlementAccountPersistencePort.class);
  private final AccountRealNameVerificationPort verificationPort =
      mock(AccountRealNameVerificationPort.class);
  private final SensitiveDataCipher cipher = new TestSensitiveDataCipher();
  private final SellerSettlementAccountService service =
      new SellerSettlementAccountService(persistencePort, cipher);

  @BeforeEach
  void setUp() {
    when(persistencePort.findByStoreId(any())).thenReturn(Optional.empty());
    when(persistencePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  @DisplayName("사업자 정산계좌를 외부 실명조회 없이 암호화하여 저장한다")
  void registersBusinessSettlementAccount() {
    UUID storeId = UUID.randomUUID();

    SellerSettlementAccountResult result = service.register(new RegisterSellerSettlementAccountCommand(
        storeId, "004", "123-456-789012", " 홍길동 ", "1234567890"));

    ArgumentCaptor<SellerSettlementAccount> accountCaptor =
        ArgumentCaptor.forClass(SellerSettlementAccount.class);
    verify(persistencePort).save(accountCaptor.capture());
    verify(verificationPort, never()).verify(any());
    SellerSettlementAccount account = accountCaptor.getValue();
    assertEquals(AccountHolderType.BUSINESS, account.getAccountHolderType());
    assertEquals("encrypted:123456789012", account.getEncryptedAccountNumber());
    assertEquals("encrypted:홍길동", account.getEncryptedAccountHolderName());
    assertEquals("encrypted:1234567890", account.getEncryptedBusinessRegistrationNumber());
    assertNull(account.getProviderTransactionId());
    assertNull(account.getVerifiedAt());
    assertEquals("********9012", result.accountNumberMasked());
    assertEquals("KB국민은행", result.bankName());
    assertEquals("******7890", result.businessRegistrationNumberMasked());
    assertNull(result.verifiedAt());
  }

  @Test
  @DisplayName("사업자등록번호의 구분자를 제거해 저장한다")
  void normalizesBusinessRegistrationNumber() {
    service.register(new RegisterSellerSettlementAccountCommand(
        UUID.randomUUID(), "088", "1234567890", "위하다", "123-45-67890"));

    ArgumentCaptor<SellerSettlementAccount> accountCaptor =
        ArgumentCaptor.forClass(SellerSettlementAccount.class);
    verify(persistencePort).save(accountCaptor.capture());
    assertEquals(
        "encrypted:1234567890",
        accountCaptor.getValue().getEncryptedBusinessRegistrationNumber());
  }

  @Test
  @DisplayName("10자리가 아닌 사업자등록번호는 거절한다")
  void rejectsInvalidBusinessRegistrationNumber() {
    BaseException exception = assertThrows(
        BaseException.class,
        () -> service.register(new RegisterSellerSettlementAccountCommand(
            UUID.randomUUID(), "004", "123456789012", "홍길동", "123456789")));

    assertEquals(AccountErrorCode.SETTLEMENT_ACCOUNT_INPUT_INVALID, exception.getErrorCode());
    verify(persistencePort, never()).save(any());
    verify(verificationPort, never()).verify(any());
  }

  @Test
  @DisplayName("정산계좌 변경 등록은 기존 계좌를 사업자 계좌로 교체한다")
  void replacesSettlementAccount() {
    UUID storeId = UUID.randomUUID();
    SellerSettlementAccount existing = SellerSettlementAccount.create(
        storeId,
        "004",
        "encrypted:old-account",
        "encrypted:이전",
        AccountHolderType.PERSONAL,
        "provider-transaction",
        Instant.parse("2026-09-11T01:00:00Z"));
    when(persistencePort.findByStoreId(storeId)).thenReturn(Optional.of(existing));

    service.register(new RegisterSellerSettlementAccountCommand(
        storeId, "090", "987654321", "새 예금주", "987-65-43210"));

    assertEquals(AccountHolderType.BUSINESS, existing.getAccountHolderType());
    assertEquals("090", existing.getBankCode());
    assertEquals("encrypted:987654321", existing.getEncryptedAccountNumber());
    assertEquals("encrypted:새 예금주", existing.getEncryptedAccountHolderName());
    assertEquals("encrypted:9876543210", existing.getEncryptedBusinessRegistrationNumber());
    assertNull(existing.getProviderTransactionId());
    assertNull(existing.getVerifiedAt());
  }

  @Test
  @DisplayName("지원하지 않는 금융기관 코드를 거부한다")
  void rejectsUnsupportedBank() {
    BaseException exception = assertThrows(
        BaseException.class,
        () -> service.register(new RegisterSellerSettlementAccountCommand(
            UUID.randomUUID(), "999", "123456789012", "홍길동", "1234567890")));

    assertEquals(AccountErrorCode.SETTLEMENT_BANK_UNSUPPORTED, exception.getErrorCode());
    verify(verificationPort, never()).verify(any());
  }

  @Test
  @DisplayName("정산계좌 조회 시 복호화한 예금주와 마스킹 정보를 반환한다")
  void getsSettlementAccount() {
    UUID storeId = UUID.randomUUID();
    SellerSettlementAccount account = SellerSettlementAccount.createRegisteredBusinessAccount(
        storeId,
        "090",
        "encrypted:1234567890123",
        "encrypted:홍길동",
        "encrypted:1234567890");
    when(persistencePort.findByStoreId(storeId)).thenReturn(Optional.of(account));

    SellerSettlementAccountResult result = service.get(storeId);

    assertEquals("카카오뱅크", result.bankName());
    assertEquals("*********0123", result.accountNumberMasked());
    assertEquals("홍길동", result.accountHolderName());
    assertEquals("******7890", result.businessRegistrationNumberMasked());
  }

  @Test
  @DisplayName("기존 데이터에 사업자등록번호가 없어도 정산계좌 조회는 유지한다")
  void getsLegacySettlementAccount() {
    UUID storeId = UUID.randomUUID();
    SellerSettlementAccount account = SellerSettlementAccount.create(
        storeId,
        "090",
        "encrypted:1234567890123",
        "encrypted:홍길동",
        AccountHolderType.PERSONAL,
        "provider-transaction",
        Instant.parse("2026-09-11T01:00:00Z"));
    when(persistencePort.findByStoreId(storeId)).thenReturn(Optional.of(account));

    SellerSettlementAccountResult result = service.get(storeId);

    assertEquals("카카오뱅크", result.bankName());
    assertEquals("*********0123", result.accountNumberMasked());
    assertNull(result.businessRegistrationNumberMasked());
  }

  @Test
  @DisplayName("운영자 정산계좌 조회 시 원문 계좌번호와 마스킹 사업자번호를 반환한다")
  void getsSettlementAccountForOperator() {
    UUID storeId = UUID.randomUUID();
    SellerSettlementAccount account = SellerSettlementAccount.createRegisteredBusinessAccount(
        storeId,
        "090",
        "encrypted:1234567890123",
        "encrypted:홍길동",
        "encrypted:1234567890");
    when(persistencePort.findByStoreId(storeId)).thenReturn(Optional.of(account));

    OperatorSettlementAccountResult result = service.getForOperator(storeId);

    assertEquals("090", result.bankCode());
    assertEquals("카카오뱅크", result.bankName());
    assertEquals("홍길동", result.accountHolderName());
    assertEquals("1234567890123", result.accountNumber());
    assertEquals("******7890", result.businessRegistrationNumberMasked());
  }

  @Test
  @DisplayName("민감정보는 평문 그대로 저장하지 않는다")
  void doesNotStoreSensitiveDataAsPlainText() {
    service.register(new RegisterSellerSettlementAccountCommand(
        UUID.randomUUID(), "004", "123456789012", "홍길동", "1234567890"));

    ArgumentCaptor<SellerSettlementAccount> accountCaptor =
        ArgumentCaptor.forClass(SellerSettlementAccount.class);
    verify(persistencePort).save(accountCaptor.capture());
    SellerSettlementAccount account = accountCaptor.getValue();
    assertNotEquals("123456789012", account.getEncryptedAccountNumber());
    assertNotEquals("홍길동", account.getEncryptedAccountHolderName());
    assertNotEquals("1234567890", account.getEncryptedBusinessRegistrationNumber());
  }

  @Test
  @DisplayName("운영자 정산계좌 조회 시 미등록 계좌는 찾을 수 없다")
  void rejectsMissingSettlementAccountForOperator() {
    BaseException exception = assertThrows(
        BaseException.class, () -> service.getForOperator(UUID.randomUUID()));

    assertEquals(AccountErrorCode.SETTLEMENT_ACCOUNT_NOT_FOUND, exception.getErrorCode());
  }

  private static final class TestSensitiveDataCipher implements SensitiveDataCipher {

    @Override
    public String encrypt(String plaintext) {
      return "encrypted:" + plaintext;
    }

    @Override
    public String decrypt(String encryptedValue) {
      return encryptedValue.substring("encrypted:".length());
    }
  }
}
