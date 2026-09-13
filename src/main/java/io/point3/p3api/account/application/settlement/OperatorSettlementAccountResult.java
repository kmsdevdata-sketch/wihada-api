package io.point3.p3api.account.application.settlement;

public record OperatorSettlementAccountResult(
    String bankCode,
    String bankName,
    String accountHolderName,
    String accountNumber,
    String businessRegistrationNumberMasked) {}
