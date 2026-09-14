package io.point3.p3api.inquiry.application.submission.result;

import io.point3.p3api.inquiry.domain.entity.OrderFormSubmission;
import io.point3.p3api.order.application.result.OrderOptionRow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record OrderFormSubmissionResult(
    UUID id,
    UUID inquiryId,
    UUID templateId,
    UUID submittedBy,
    LocalDate pickupDate,
    LocalTime pickupTime,
    String answers,
    List<OrderFormReferenceAssetResult> referenceAssets,
    List<OrderOptionRow> optionRows,
    boolean cancellationRefundAgreed,
    Instant sellerViewedAt,
    boolean current,
    Instant submittedAt) {

  public OrderFormSubmissionResult {
    referenceAssets = referenceAssets == null ? List.of() : List.copyOf(referenceAssets);
    optionRows = List.copyOf(optionRows);
  }

  public static OrderFormSubmissionResult from(
      OrderFormSubmission submission,
      String answers,
      List<OrderFormReferenceAssetResult> referenceAssets,
      List<OrderOptionRow> optionRows,
      UUID currentOrderFormSubmissionId) {
    return new OrderFormSubmissionResult(
        submission.getId(),
        submission.getInquiryId(),
        submission.getTemplateId(),
        submission.getSubmittedBy(),
        submission.getPickupDate(),
        submission.getPickupTime(),
        answers,
        referenceAssets,
        optionRows,
        submission.isCancellationRefundAgreed(),
        submission.getSellerViewedAt(),
        submission.getId().equals(currentOrderFormSubmissionId),
        submission.getSubmittedAt());
  }

  public boolean sellerViewed() {
    return sellerViewedAt != null;
  }

  @Override
  public List<OrderFormReferenceAssetResult> referenceAssets() {
    return List.copyOf(referenceAssets);
  }

  @Override
  public List<OrderOptionRow> optionRows() {
    return List.copyOf(optionRows);
  }
}
