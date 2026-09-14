package io.point3.p3api.dashboard.controller.response;

import io.point3.p3api.dashboard.application.result.SellerRevenueResult;
import java.time.LocalDate;

public record SellerRevenueResponse(
    LocalDate startDate,
    LocalDate endDate,
    long paymentRevenueAmount,
    long succeededPaymentCount,
    long completedRefundAmount,
    long completedRefundCount,
    long netSalesAmount,
    int settlementFeeRateBasisPoints,
    long settlementFeeAmount,
    long settlementEstimateAmount) {

  public static SellerRevenueResponse from(SellerRevenueResult result) {
    return new SellerRevenueResponse(
        result.startDate(),
        result.endDate(),
        result.paymentRevenueAmount(),
        result.succeededPaymentCount(),
        result.completedRefundAmount(),
        result.completedRefundCount(),
        result.netSalesAmount(),
        result.settlementFeeRateBasisPoints(),
        result.settlementFeeAmount(),
        result.settlementEstimateAmount());
  }
}
