package io.point3.p3api.order.application.result;

import io.point3.p3api.order.application.refund.OrderRefundCalculation;
import io.point3.p3api.order.application.refund.OrderRefundCalculationBasis;
import java.time.Instant;
import java.util.UUID;

public record OrderRefundQuoteResult(
    UUID orderId,
    long paidAmount,
    long refundAmount,
    int refundRate,
    Instant calculationBaseAt,
    OrderRefundCalculationBasis calculationBasis) {

  public static OrderRefundQuoteResult from(UUID orderId, OrderRefundCalculation calculation) {
    return new OrderRefundQuoteResult(
        orderId,
        calculation.paidAmount(),
        calculation.refundAmount(),
        calculation.refundRate(),
        calculation.calculationBaseAt(),
        calculation.calculationBasis());
  }
}
