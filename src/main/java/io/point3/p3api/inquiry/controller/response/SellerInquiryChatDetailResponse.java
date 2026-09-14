package io.point3.p3api.inquiry.controller.response;

import io.point3.p3api.inquiry.application.result.InquiryChatDetail;
import io.point3.p3api.inquiry.application.result.OrderStartReferenceAssetResult;
import io.point3.p3api.inquiry.domain.type.OrderFormReferenceAssetSource;
import java.time.Instant;
import java.util.UUID;

public record SellerInquiryChatDetailResponse(
    UUID inquiryId,
    UUID storeId,
    String storeName,
    String storeSlug,
    ParticipantResponse participant,
    StartReferenceAssetResponse startReferenceAsset,
    UUID currentOrderFormSubmissionId,
    Instant myLastReadAt,
    Instant participantLastReadAt,
    Instant createdAt) {

  public static SellerInquiryChatDetailResponse from(InquiryChatDetail detail) {
    return new SellerInquiryChatDetailResponse(
        detail.inquiryId(),
        detail.storeId(),
        detail.storeName(),
        detail.storeSlug(),
        ParticipantResponse.from(detail.participant()),
        StartReferenceAssetResponse.from(detail.startReferenceAsset()),
        detail.currentOrderFormSubmissionId(),
        detail.myLastReadAt(),
        detail.participantLastReadAt(),
        detail.createdAt());
  }

  public record ParticipantResponse(
      UUID userId, String name, String phoneNumber, String profileImageDeliveryUrl) {

    private static ParticipantResponse from(InquiryChatDetail.Participant participant) {
      return new ParticipantResponse(
          participant.userId(),
          participant.name(),
          participant.phoneNumber(),
          participant.profileImageDeliveryUrl());
    }
  }

  public record StartReferenceAssetResponse(
      UUID assetId, OrderFormReferenceAssetSource source, String deliveryUrl) {

    private static StartReferenceAssetResponse from(OrderStartReferenceAssetResult result) {
      if (result == null) {
        return null;
      }
      return new StartReferenceAssetResponse(
          result.assetId(), result.source(), result.deliveryUrl());
    }
  }
}
