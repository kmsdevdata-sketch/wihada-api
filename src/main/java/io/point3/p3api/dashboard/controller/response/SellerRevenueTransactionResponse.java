package io.point3.p3api.dashboard.controller.response;

import io.point3.p3api.dashboard.application.query.SellerRevenueTransactionType;
import io.point3.p3api.dashboard.application.result.SellerRevenueTransactionResult;
import io.point3.p3api.order.controller.response.ReferenceAssetResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SellerRevenueTransactionResponse(
    SellerRevenueTransactionType transactionType,
    UUID transactionId,
    UUID orderId,
    UUID paymentAttemptId,
    UUID refundId,
    UUID inquiryId,
    long amount,
    Instant occurredAt,
    Instant pickupAt,
    String buyerName,
    List<ReferenceAssetResponse> referenceAssets) {

  public SellerRevenueTransactionResponse {
    referenceAssets = List.copyOf(referenceAssets);
  }

  public static SellerRevenueTransactionResponse from(SellerRevenueTransactionResult result) {
    return new SellerRevenueTransactionResponse(
        result.transactionType(),
        result.transactionId(),
        result.orderId(),
        result.paymentAttemptId(),
        result.refundId(),
        result.inquiryId(),
        result.amount(),
        result.occurredAt(),
        result.pickupAt(),
        result.buyerName(),
        result.referenceAssets().stream().map(ReferenceAssetResponse::from).toList());
  }

  @Override
  public List<ReferenceAssetResponse> referenceAssets() {
    return List.copyOf(referenceAssets);
  }
}
