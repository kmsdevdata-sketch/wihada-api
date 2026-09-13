package io.point3.p3api.account.application.settlement;

import java.util.UUID;

public record RegisterSellerSettlementAccountCommand(
    UUID storeId,
    String bankCode,
    String accountNumber,
    String accountHolderName,
    String businessRegistrationNumber) {}
