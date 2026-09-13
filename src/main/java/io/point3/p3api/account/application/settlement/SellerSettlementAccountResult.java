package io.point3.p3api.account.application.settlement;

import java.time.Instant;

public record SellerSettlementAccountResult(
    String bankCode,
    String bankName,
    String accountNumberMasked,
    String accountHolderName,
    String businessRegistrationNumberMasked,
    Instant verifiedAt) {}
