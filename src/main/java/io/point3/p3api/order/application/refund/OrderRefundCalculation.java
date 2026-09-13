package io.point3.p3api.order.application.refund;

import java.time.Instant;

public record OrderRefundCalculation(
    long paidAmount,
    long refundAmount,
    int refundRate,
    Instant calculationBaseAt,
    OrderRefundCalculationBasis calculationBasis) {}
