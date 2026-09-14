package io.point3.p3api.order.controller.response;

import io.point3.p3api.inquiry.application.submission.result.OrderFormSubmissionResult;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record OrderFormSubmissionResponse(
    UUID id,
    UUID inquiryId,
    UUID templateId,
    UUID submittedBy,
    LocalDate pickupDate,
    LocalTime pickupTime,
    String answers,
    List<ReferenceAssetResponse> referenceAssets,
    List<OrderOptionRowResponse> optionRows,
    boolean cancellationRefundAgreed,
    Instant sellerViewedAt,
    boolean sellerViewed,
    boolean current,
    Instant submittedAt) {

  public OrderFormSubmissionResponse {
    referenceAssets = List.copyOf(referenceAssets);
    optionRows = List.copyOf(optionRows);
  }

  public static OrderFormSubmissionResponse from(OrderFormSubmissionResult submission) {
    return new OrderFormSubmissionResponse(
        submission.id(),
        submission.inquiryId(),
        submission.templateId(),
        submission.submittedBy(),
        submission.pickupDate(),
        submission.pickupTime(),
        submission.answers(),
        submission.referenceAssets().stream().map(ReferenceAssetResponse::from).toList(),
        submission.optionRows().stream().map(OrderOptionRowResponse::from).toList(),
        submission.cancellationRefundAgreed(),
        submission.sellerViewedAt(),
        submission.sellerViewed(),
        submission.current(),
        submission.submittedAt());
  }

  @Override
  public List<ReferenceAssetResponse> referenceAssets() {
    return List.copyOf(referenceAssets);
  }

  @Override
  public List<OrderOptionRowResponse> optionRows() {
    return List.copyOf(optionRows);
  }
}
