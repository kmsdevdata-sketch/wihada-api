package io.point3.p3api.dashboard.application.result;

import io.point3.p3api.dashboard.application.query.SellerRevenueTransactionType;
import io.point3.p3api.order.application.result.OrderReferenceAssetResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SellerRevenueTransactionResult(
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
    List<OrderReferenceAssetResult> referenceAssets) {

  public SellerRevenueTransactionResult {
    referenceAssets = List.copyOf(referenceAssets);
  }

  @Override
  public List<OrderReferenceAssetResult> referenceAssets() {
    return List.copyOf(referenceAssets);
  }
}
