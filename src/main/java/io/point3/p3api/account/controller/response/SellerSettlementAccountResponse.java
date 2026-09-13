package io.point3.p3api.account.controller.response;

import io.point3.p3api.account.application.settlement.SellerSettlementAccountResult;
import java.time.Instant;

public record SellerSettlementAccountResponse(
    String bankCode,
    String bankName,
    String accountNumberMasked,
    String accountHolderName,
    String businessRegistrationNumberMasked,
    String registrationStatus,
    String verificationStatus,
    Instant verifiedAt) {

  private static final String REGISTERED = "REGISTERED";
  private static final String UNVERIFIED = "UNVERIFIED";

  public static SellerSettlementAccountResponse from(SellerSettlementAccountResult result) {
    return new SellerSettlementAccountResponse(
        result.bankCode(),
        result.bankName(),
        result.accountNumberMasked(),
        result.accountHolderName(),
        result.businessRegistrationNumberMasked(),
        REGISTERED,
        UNVERIFIED,
        null);
  }
}
