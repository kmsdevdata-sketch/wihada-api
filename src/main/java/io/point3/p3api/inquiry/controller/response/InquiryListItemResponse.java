package io.point3.p3api.inquiry.controller.response;

import io.point3.p3api.chat.domain.type.ChatTimelineItemType;
import io.point3.p3api.inquiry.application.result.InquiryListItem;
import io.point3.p3api.inquiry.domain.type.InquiryStatus;
import java.time.Instant;
import java.util.UUID;

public record InquiryListItemResponse(
    UUID inquiryId,
    UUID storeId,
    InquiryStatus status,
    String storeName,
    String storeSlug,
    InquiryChatDetailResponse.ParticipantResponse participant,
    long unreadCount,
    Instant latestEventAt,
    LatestEventResponse latestEvent,
    LatestOrderFormSubmissionResponse latestOrderFormSubmission,
    UUID currentOrderFormSubmissionId,
    Instant myLastReadAt,
    Instant createdAt) {

  public static InquiryListItemResponse from(InquiryListItem item) {
    var detail = InquiryChatDetailResponse.from(item.detail());
    return new InquiryListItemResponse(
        detail.inquiryId(),
        detail.storeId(),
        item.status(),
        detail.storeName(),
        detail.storeSlug(),
        detail.participant(),
        item.unreadCount(),
        item.latestEventAt(),
        LatestEventResponse.from(item.latestEvent()),
        LatestOrderFormSubmissionResponse.from(item.latestOrderFormSubmission()),
        detail.currentOrderFormSubmissionId(),
        detail.myLastReadAt(),
        detail.createdAt());
  }

  public record LatestEventResponse(
      UUID eventId,
      UUID referenceId,
      ChatTimelineItemType type,
      UUID senderUserId,
      String content,
      Instant createdAt) {

    private static LatestEventResponse from(InquiryListItem.LatestEvent latestEvent) {
      if (latestEvent == null) {
        return null;
      }
      return new LatestEventResponse(
          latestEvent.eventId(),
          latestEvent.referenceId(),
          latestEvent.type(),
          latestEvent.senderUserId(),
          latestEvent.content(),
          latestEvent.createdAt());
    }
  }

  public record LatestOrderFormSubmissionResponse(UUID submissionId, Instant submittedAt) {

    private static LatestOrderFormSubmissionResponse from(
        InquiryListItem.LatestOrderFormSubmission latestOrderFormSubmission) {
      if (latestOrderFormSubmission == null) {
        return null;
      }
      return new LatestOrderFormSubmissionResponse(
          latestOrderFormSubmission.submissionId(), latestOrderFormSubmission.submittedAt());
    }
  }
}
