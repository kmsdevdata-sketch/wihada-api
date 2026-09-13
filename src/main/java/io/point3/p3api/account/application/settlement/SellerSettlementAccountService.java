package io.point3.p3api.account.application.settlement;

import io.point3.p3api.account.application.port.SensitiveDataCipher;
import io.point3.p3api.account.application.settlement.port.SellerSettlementAccountPersistencePort;
import io.point3.p3api.account.domain.entity.SellerSettlementAccount;
import io.point3.p3api.account.domain.type.SettlementBank;
import io.point3.p3api.account.domain.value.BankAccountNumber;
import io.point3.p3api.account.domain.value.BusinessRegistrationNumber;
import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.AccountErrorCode;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SellerSettlementAccountService
    implements SellerSettlementAccountRegisterUseCase,
        SellerSettlementAccountQueryUseCase,
        OperatorSettlementAccountQueryUseCase,
        SettlementBankQueryUseCase {

  private final SellerSettlementAccountPersistencePort persistencePort;
  private final SensitiveDataCipher sensitiveDataCipher;

  @Override
  public SellerSettlementAccountResult register(RegisterSellerSettlementAccountCommand command) {
    SettlementBank bank = findBank(command.bankCode());
    BankAccountNumber accountNumber = accountNumber(command.accountNumber());
    String accountHolderName = requireText(command.accountHolderName());
    BusinessRegistrationNumber businessRegistrationNumber =
        businessRegistrationNumber(command.businessRegistrationNumber());

    SellerSettlementAccount account = persistencePort
        .findByStoreId(command.storeId())
        .map(existing ->
            replace(existing, bank, accountNumber, accountHolderName, businessRegistrationNumber))
        .orElseGet(() -> create(
            command.storeId(), bank, accountNumber, accountHolderName, businessRegistrationNumber));

    return toResult(persistencePort.save(account));
  }

  @Override
  public SellerSettlementAccountResult get(UUID storeId) {
    SellerSettlementAccount account = persistencePort
        .findByStoreId(storeId)
        .orElseThrow(() -> new BaseException(AccountErrorCode.SETTLEMENT_ACCOUNT_NOT_FOUND));
    return toResult(account);
  }

  @Override
  public OperatorSettlementAccountResult getForOperator(UUID storeId) {
    SellerSettlementAccount account = persistencePort
        .findByStoreId(storeId)
        .orElseThrow(() -> new BaseException(AccountErrorCode.SETTLEMENT_ACCOUNT_NOT_FOUND));
    SettlementBank bank = findBank(account.getBankCode());
    BankAccountNumber accountNumber =
        accountNumber(sensitiveDataCipher.decrypt(account.getEncryptedAccountNumber()));
    String businessRegistrationNumberMasked = businessRegistrationNumberMasked(account);
    return new OperatorSettlementAccountResult(
        bank.code(),
        bank.displayName(),
        sensitiveDataCipher.decrypt(account.getEncryptedAccountHolderName()),
        accountNumber.value(),
        businessRegistrationNumberMasked);
  }

  @Override
  public List<SettlementBankResult> getBanks() {
    return Arrays.stream(SettlementBank.values())
        .map(SettlementBankResult::from)
        .toList();
  }

  private SellerSettlementAccount create(
      UUID storeId,
      SettlementBank bank,
      BankAccountNumber accountNumber,
      String accountHolderName,
      BusinessRegistrationNumber businessRegistrationNumber) {
    return SellerSettlementAccount.createRegisteredBusinessAccount(
        storeId,
        bank.code(),
        sensitiveDataCipher.encrypt(accountNumber.value()),
        sensitiveDataCipher.encrypt(accountHolderName),
        sensitiveDataCipher.encrypt(businessRegistrationNumber.value()));
  }

  private SellerSettlementAccount replace(
      SellerSettlementAccount account,
      SettlementBank bank,
      BankAccountNumber accountNumber,
      String accountHolderName,
      BusinessRegistrationNumber businessRegistrationNumber) {
    account.replaceRegisteredBusinessAccount(
        bank.code(),
        sensitiveDataCipher.encrypt(accountNumber.value()),
        sensitiveDataCipher.encrypt(accountHolderName),
        sensitiveDataCipher.encrypt(businessRegistrationNumber.value()));
    return account;
  }

  private SellerSettlementAccountResult toResult(SellerSettlementAccount account) {
    SettlementBank bank = findBank(account.getBankCode());
    BankAccountNumber accountNumber =
        accountNumber(sensitiveDataCipher.decrypt(account.getEncryptedAccountNumber()));
    return new SellerSettlementAccountResult(
        bank.code(),
        bank.displayName(),
        accountNumber.masked(),
        sensitiveDataCipher.decrypt(account.getEncryptedAccountHolderName()),
        businessRegistrationNumberMasked(account),
        account.getVerifiedAt());
  }

  private SettlementBank findBank(String bankCode) {
    return SettlementBank.findByCode(bankCode)
        .orElseThrow(() -> new BaseException(AccountErrorCode.SETTLEMENT_BANK_UNSUPPORTED));
  }

  private BankAccountNumber accountNumber(String value) {
    if (value == null) {
      throw new BaseException(AccountErrorCode.SETTLEMENT_ACCOUNT_INPUT_INVALID);
    }
    try {
      return BankAccountNumber.of(value);
    } catch (IllegalArgumentException exception) {
      throw new BaseException(AccountErrorCode.SETTLEMENT_ACCOUNT_INPUT_INVALID);
    }
  }

  private BusinessRegistrationNumber businessRegistrationNumber(String value) {
    if (value == null) {
      throw new BaseException(AccountErrorCode.SETTLEMENT_ACCOUNT_INPUT_INVALID);
    }
    try {
      return BusinessRegistrationNumber.of(value);
    } catch (IllegalArgumentException exception) {
      throw new BaseException(AccountErrorCode.SETTLEMENT_ACCOUNT_INPUT_INVALID);
    }
  }

  private String requireText(String value) {
    if (value == null || value.isBlank()) {
      throw new BaseException(AccountErrorCode.SETTLEMENT_ACCOUNT_INPUT_INVALID);
    }
    return value.trim();
  }

  private String businessRegistrationNumberMasked(SellerSettlementAccount account) {
    String encryptedNumber = account.getEncryptedBusinessRegistrationNumber();
    if (encryptedNumber == null || encryptedNumber.isBlank()) {
      return null;
    }
    return BusinessRegistrationNumber.of(sensitiveDataCipher.decrypt(encryptedNumber))
        .masked();
  }
}
