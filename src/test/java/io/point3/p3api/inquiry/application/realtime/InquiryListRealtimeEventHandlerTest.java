package io.point3.p3api.inquiry.application.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.point3.p3api.chat.application.port.ChatTimelineItemPort;
import io.point3.p3api.inquiry.application.port.InquiryPersistencePort;
import io.point3.p3api.inquiry.domain.entity.Inquiry;
import io.point3.p3api.inquiry.domain.type.InquiryStatus;
import io.point3.p3api.store.application.port.StorePersistencePort;
import io.point3.p3api.store.domain.entity.Store;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InquiryListRealtimeEventHandlerTest {

  private final InquiryPersistencePort inquiryPersistencePort = mock(InquiryPersistencePort.class);
  private final StorePersistencePort storePersistencePort = mock(StorePersistencePort.class);
  private final ChatTimelineItemPort chatTimelineItemPort = mock(ChatTimelineItemPort.class);
  private final InquiryListRealtimePublisherPort realtimePublisherPort =
      mock(InquiryListRealtimePublisherPort.class);
  private final InquiryListRealtimeEventHandler handler = new InquiryListRealtimeEventHandler(
      inquiryPersistencePort, storePersistencePort, chatTimelineItemPort, realtimePublisherPort);

  @Test
  @DisplayName("문의 변경 이벤트는 구매자와 판매자 목록 payload를 각각 발행한다")
  void publishesInquiryChangedToBuyerAndSeller() {
    UUID inquiryId = UUID.randomUUID();
    UUID buyerUserId = UUID.randomUUID();
    UUID sellerUserId = UUID.randomUUID();
    Instant buyerReadAt = Instant.parse("2026-09-07T00:00:00Z");
    Instant sellerReadAt = Instant.parse("2026-09-07T00:01:00Z");
    Instant latestEventAt = Instant.parse("2026-09-07T00:02:00Z");
    UUID currentSubmissionId = UUID.randomUUID();
    Inquiry inquiry = inquiry(
        inquiryId,
        buyerUserId,
        sellerReadAt,
        buyerReadAt,
        InquiryStatus.WAITING,
        currentSubmissionId);
    Store store = store(sellerUserId);

    when(inquiryPersistencePort.findById(inquiryId)).thenReturn(java.util.Optional.of(inquiry));
    when(storePersistencePort.findById(inquiry.getStoreId()))
        .thenReturn(java.util.Optional.of(store));
    when(chatTimelineItemPort.findLatestCreatedAt(inquiryId)).thenReturn(latestEventAt);
    when(chatTimelineItemPort.countUnread(inquiryId, buyerUserId, buyerReadAt)).thenReturn(2L);
    when(chatTimelineItemPort.countUnread(inquiryId, sellerUserId, sellerReadAt))
        .thenReturn(0L);

    handler.publish(new InquiryListChangedEvent(inquiryId));

    List<InquiryListRealtimeEvent> events = capturedEvents();
    InquiryListRealtimePayload buyerPayload = payloadFor(events, buyerUserId);
    InquiryListRealtimePayload sellerPayload = payloadFor(events, sellerUserId);
    assertEquals("INQUIRY_UPDATED", buyerPayload.type());
    assertEquals(inquiryId, buyerPayload.inquiryId());
    assertEquals(2, buyerPayload.unreadCount());
    assertEquals(latestEventAt, buyerPayload.latestEventAt());
    assertEquals(InquiryStatus.WAITING, buyerPayload.status());
    assertEquals(currentSubmissionId, buyerPayload.currentOrderFormSubmissionId());
    assertEquals(currentSubmissionId, sellerPayload.currentOrderFormSubmissionId());
    assertEquals(0, sellerPayload.unreadCount());
  }

  @Test
  @DisplayName("읽음 변경 이벤트는 해당 reader의 목록 payload만 발행한다")
  void publishesReaderChangedOnlyToReader() {
    UUID inquiryId = UUID.randomUUID();
    UUID buyerUserId = UUID.randomUUID();
    Instant buyerReadAt = Instant.parse("2026-09-07T00:00:00Z");
    Instant latestEventAt = Instant.parse("2026-09-07T00:02:00Z");
    Inquiry inquiry = inquiry(
        inquiryId,
        buyerUserId,
        Instant.parse("2026-09-07T00:01:00Z"),
        buyerReadAt,
        InquiryStatus.WAITING,
        null);

    when(inquiryPersistencePort.findById(inquiryId)).thenReturn(java.util.Optional.of(inquiry));
    when(chatTimelineItemPort.findLatestCreatedAt(inquiryId)).thenReturn(latestEventAt);
    when(chatTimelineItemPort.countUnread(inquiryId, buyerUserId, buyerReadAt)).thenReturn(0L);

    handler.publish(new InquiryListReaderChangedEvent(inquiryId, buyerUserId));

    List<InquiryListRealtimeEvent> events = capturedEvents();
    assertEquals(1, events.size());
    assertEquals(buyerUserId, events.getFirst().userId());
    assertEquals(0, events.getFirst().payload().unreadCount());
    verifyNoInteractions(storePersistencePort);
  }

  private List<InquiryListRealtimeEvent> capturedEvents() {
    ArgumentCaptor<InquiryListRealtimeEvent> captor =
        ArgumentCaptor.forClass(InquiryListRealtimeEvent.class);
    verify(realtimePublisherPort, org.mockito.Mockito.atLeastOnce()).publish(captor.capture());
    return captor.getAllValues();
  }

  private InquiryListRealtimePayload payloadFor(
      List<InquiryListRealtimeEvent> events, UUID userId) {
    return events.stream()
        .filter(event -> event.userId().equals(userId))
        .findFirst()
        .orElseThrow()
        .payload();
  }

  private Inquiry inquiry(
      UUID inquiryId,
      UUID buyerUserId,
      Instant sellerReadAt,
      Instant buyerReadAt,
      InquiryStatus status,
      UUID currentSubmissionId) {
    Inquiry inquiry = mock(Inquiry.class);
    when(inquiry.getId()).thenReturn(inquiryId);
    when(inquiry.getStoreId()).thenReturn(UUID.randomUUID());
    when(inquiry.getBuyerUserId()).thenReturn(buyerUserId);
    when(inquiry.getBuyerLastReadAt()).thenReturn(buyerReadAt);
    when(inquiry.getSellerLastReadAt()).thenReturn(sellerReadAt);
    when(inquiry.isBuyerVisible()).thenReturn(true);
    when(inquiry.isSellerVisible()).thenReturn(true);
    when(inquiry.statusForBuyer()).thenReturn(status);
    when(inquiry.statusForSeller()).thenReturn(status);
    when(inquiry.getCurrentOrderFormSubmissionId()).thenReturn(currentSubmissionId);
    when(inquiry.getCreatedAt()).thenReturn(Instant.parse("2026-09-06T00:00:00Z"));
    return inquiry;
  }

  private Store store(UUID sellerUserId) {
    Store store = mock(Store.class);
    when(store.getOwnerUserId()).thenReturn(sellerUserId);
    return store;
  }
}
