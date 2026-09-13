package io.point3.p3api.order.controller.response;

import io.point3.p3api.order.application.refund.OrderRefundCalculationBasis;
import io.point3.p3api.order.application.result.OrderRefundQuoteResult;
import java.time.Instant;
import java.util.UUID;

public record SellerOrderRefundQuoteResponse(
    UUID orderId,
    long paidAmount,
    long refundAmount,
    int refundRate,
    Instant calculationBaseAt,
    OrderRefundCalculationBasis calculationBasis) {

  public static SellerOrderRefundQuoteResponse from(OrderRefundQuoteResult result) {
    return new SellerOrderRefundQuoteResponse(
        result.orderId(),
        result.paidAmount(),
        result.refundAmount(),
        result.refundRate(),
        result.calculationBaseAt(),
        result.calculationBasis());
  }
}
