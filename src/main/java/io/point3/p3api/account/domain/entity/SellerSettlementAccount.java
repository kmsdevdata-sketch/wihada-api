package io.point3.p3api.account.domain.entity;

import io.point3.p3api.account.domain.type.AccountHolderType;
import io.point3.p3api.account.domain.type.SettlementBank;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
    name = "seller_settlement_accounts",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_seller_settlement_accounts_store_id",
            columnNames = "store_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerSettlementAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "store_id", nullable = false)
  private UUID storeId;

  @Column(name = "bank_code", nullable = false, length = 3)
  private String bankCode;

  @Column(name = "encrypted_account_number", nullable = false, columnDefinition = "text")
  private String encryptedAccountNumber;

  @Column(name = "encrypted_account_holder_name", nullable = false, columnDefinition = "text")
  private String encryptedAccountHolderName;

  @Column(name = "encrypted_business_registration_number", columnDefinition = "text")
  private String encryptedBusinessRegistrationNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "account_holder_type", nullable = false, length = 20)
  private AccountHolderType accountHolderType;

  @Column(name = "provider_transaction_id", length = 40)
  private String providerTransactionId;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  private SellerSettlementAccount(
      UUID storeId,
      String bankCode,
      String encryptedAccountNumber,
      String encryptedAccountHolderName,
      AccountHolderType accountHolderType,
      String providerTransactionId,
      Instant verifiedAt) {
    this.storeId = storeId;
    this.bankCode = bankCode;
    this.encryptedAccountNumber = encryptedAccountNumber;
    this.encryptedAccountHolderName = encryptedAccountHolderName;
    this.accountHolderType = accountHolderType;
    this.providerTransactionId = providerTransactionId;
    this.verifiedAt = verifiedAt;
  }

  public static SellerSettlementAccount create(
      UUID storeId,
      String bankCode,
      String encryptedAccountNumber,
      String encryptedAccountHolderName,
      AccountHolderType accountHolderType,
      String providerTransactionId,
      Instant verifiedAt) {
    return new SellerSettlementAccount(
        Objects.requireNonNull(storeId, "storeId"),
        validBankCode(bankCode),
        requireText(encryptedAccountNumber, "encryptedAccountNumber"),
        requireText(encryptedAccountHolderName, "encryptedAccountHolderName"),
        Objects.requireNonNull(accountHolderType, "accountHolderType"),
        requireText(providerTransactionId, "providerTransactionId"),
        Objects.requireNonNull(verifiedAt, "verifiedAt"));
  }

  public static SellerSettlementAccount createRegisteredBusinessAccount(
      UUID storeId,
      String bankCode,
      String encryptedAccountNumber,
      String encryptedAccountHolderName,
      String encryptedBusinessRegistrationNumber) {
    SellerSettlementAccount account = new SellerSettlementAccount(
        Objects.requireNonNull(storeId, "storeId"),
        validBankCode(bankCode),
        requireText(encryptedAccountNumber, "encryptedAccountNumber"),
        requireText(encryptedAccountHolderName, "encryptedAccountHolderName"),
        AccountHolderType.BUSINESS,
        null,
        null);
    account.encryptedBusinessRegistrationNumber =
        requireText(encryptedBusinessRegistrationNumber, "encryptedBusinessRegistrationNumber");
    return account;
  }

  public void replaceVerifiedAccount(
      String bankCode,
      String encryptedAccountNumber,
      String encryptedAccountHolderName,
      AccountHolderType accountHolderType,
      String providerTransactionId,
      Instant verifiedAt) {
    this.bankCode = validBankCode(bankCode);
    this.encryptedAccountNumber = requireText(encryptedAccountNumber, "encryptedAccountNumber");
    this.encryptedAccountHolderName =
        requireText(encryptedAccountHolderName, "encryptedAccountHolderName");
    this.accountHolderType = Objects.requireNonNull(accountHolderType, "accountHolderType");
    this.providerTransactionId = requireText(providerTransactionId, "providerTransactionId");
    this.verifiedAt = Objects.requireNonNull(verifiedAt, "verifiedAt");
    this.encryptedBusinessRegistrationNumber = null;
  }

  public void replaceRegisteredBusinessAccount(
      String bankCode,
      String encryptedAccountNumber,
      String encryptedAccountHolderName,
      String encryptedBusinessRegistrationNumber) {
    this.bankCode = validBankCode(bankCode);
    this.encryptedAccountNumber = requireText(encryptedAccountNumber, "encryptedAccountNumber");
    this.encryptedAccountHolderName =
        requireText(encryptedAccountHolderName, "encryptedAccountHolderName");
    this.encryptedBusinessRegistrationNumber =
        requireText(encryptedBusinessRegistrationNumber, "encryptedBusinessRegistrationNumber");
    this.accountHolderType = AccountHolderType.BUSINESS;
    this.providerTransactionId = null;
    this.verifiedAt = null;
  }

  private static String validBankCode(String value) {
    String bankCode = requireText(value, "bankCode");
    if (SettlementBank.findByCode(bankCode).isEmpty()) {
      throw new IllegalArgumentException("Settlement bank is not supported");
    }
    return bankCode;
  }

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName);
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }
}
