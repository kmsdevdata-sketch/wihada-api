package io.point3.p3api.inquiry.application.realtime;

import io.point3.p3api.chat.application.port.ChatTimelineItemPort;
import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.ChatErrorCode;
import io.point3.p3api.inquiry.application.port.InquiryPersistencePort;
import io.point3.p3api.inquiry.domain.entity.Inquiry;
import io.point3.p3api.inquiry.domain.type.InquiryStatus;
import io.point3.p3api.store.application.port.StorePersistencePort;
import io.point3.p3api.store.domain.entity.Store;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 문의 목록 갱신 이벤트를 사용자별 실시간 payload로 변환 */
@Component
@RequiredArgsConstructor
public class InquiryListRealtimeEventHandler {

  private final InquiryPersistencePort inquiryPersistencePort;
  private final StorePersistencePort storePersistencePort;
  private final ChatTimelineItemPort chatTimelineItemPort;
  private final InquiryListRealtimePublisherPort inquiryListRealtimePublisherPort;

  @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void publish(InquiryListChangedEvent event) {
    Inquiry inquiry = findInquiry(event.inquiryId());
    Store store = findStore(inquiry.getStoreId());
    Instant latestEventAt = latestEventAt(event.inquiryId(), inquiry.getCreatedAt());

    if (inquiry.isBuyerVisible()) {
      publishFor(
          event.inquiryId(),
          inquiry.getBuyerUserId(),
          inquiry.getBuyerLastReadAt(),
          latestEventAt,
          inquiry.statusForBuyer(),
          inquiry.getCurrentOrderFormSubmissionId());
    }
    if (inquiry.isSellerVisible()) {
      publishFor(
          event.inquiryId(),
          store.getOwnerUserId(),
          inquiry.getSellerLastReadAt(),
          latestEventAt,
          inquiry.statusForSeller(),
          inquiry.getCurrentOrderFormSubmissionId());
    }
  }

  @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void publish(InquiryListReaderChangedEvent event) {
    Inquiry inquiry = findInquiry(event.inquiryId());

    if (event.readerUserId().equals(inquiry.getBuyerUserId())) {
      publishFor(
          event.inquiryId(),
          inquiry.getBuyerUserId(),
          inquiry.getBuyerLastReadAt(),
          latestEventAt(event.inquiryId(), inquiry.getCreatedAt()),
          inquiry.statusForBuyer(),
          inquiry.getCurrentOrderFormSubmissionId());
      return;
    }

    Store store = findStore(inquiry.getStoreId());
    if (event.readerUserId().equals(store.getOwnerUserId())) {
      publishFor(
          event.inquiryId(),
          store.getOwnerUserId(),
          inquiry.getSellerLastReadAt(),
          latestEventAt(event.inquiryId(), inquiry.getCreatedAt()),
          inquiry.statusForSeller(),
          inquiry.getCurrentOrderFormSubmissionId());
    }
  }

  private Inquiry findInquiry(UUID inquiryId) {
    return inquiryPersistencePort
        .findById(inquiryId)
        .orElseThrow(() -> new BaseException(ChatErrorCode.CHAT_INQUIRY_NOT_FOUND));
  }

  private Store findStore(UUID storeId) {
    return storePersistencePort
        .findById(storeId)
        .orElseThrow(() -> new BaseException(ChatErrorCode.CHAT_INQUIRY_NOT_FOUND));
  }

  private Instant latestEventAt(UUID inquiryId, Instant createdAt) {
    Instant latestEventAt = chatTimelineItemPort.findLatestCreatedAt(inquiryId);
    return latestEventAt == null ? createdAt : latestEventAt;
  }

  private void publishFor(
      UUID inquiryId,
      UUID userId,
      Instant readAt,
      Instant latestEventAt,
      InquiryStatus status,
      UUID currentOrderFormSubmissionId) {
    inquiryListRealtimePublisherPort.publish(new InquiryListRealtimeEvent(
        userId,
        InquiryListRealtimePayload.inquiryUpdated(
            inquiryId,
            chatTimelineItemPort.countUnread(inquiryId, userId, readAt),
            latestEventAt,
            status,
            currentOrderFormSubmissionId)));
  }
}
