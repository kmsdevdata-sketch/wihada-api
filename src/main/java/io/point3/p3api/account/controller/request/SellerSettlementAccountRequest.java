package io.point3.p3api.account.controller.request;

import io.point3.p3api.account.application.settlement.RegisterSellerSettlementAccountCommand;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record SellerSettlementAccountRequest(
    @NotBlank String bankCode,
    @NotBlank String accountNumber,
    @NotBlank String accountHolderName,
    @NotBlank String businessRegistrationNumber) {

  public RegisterSellerSettlementAccountCommand toCommand(UUID storeId) {
    return new RegisterSellerSettlementAccountCommand(
        storeId, bankCode, accountNumber, accountHolderName, businessRegistrationNumber);
  }
}
