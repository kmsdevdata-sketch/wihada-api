package io.point3.p3api.inquiry.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.point3.p3api.inquiry.domain.type.InquiryStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InquiryTest {

  @Test
  @DisplayName("새 문의방은 접수대기 상태로 생성되고 읽음 시각을 역할별로 기록한다")
  void createsWaitingInquiryAndRecordsReadTimestamps() {
    Inquiry inquiry = Inquiry.create(UUID.randomUUID(), UUID.randomUUID());
    Instant buyerReadAt = Instant.parse("2026-08-25T00:00:00Z");
    Instant sellerReadAt = Instant.parse("2026-08-25T01:00:00Z");

    inquiry.markBuyerRead(buyerReadAt);
    inquiry.markSellerRead(sellerReadAt);

    assertEquals(InquiryStatus.WAITING, inquiry.getStatus());
    assertEquals(buyerReadAt, inquiry.getBuyerLastReadAt());
    assertEquals(sellerReadAt, inquiry.getSellerLastReadAt());
  }

  @Test
  @DisplayName("결제완료 또는 픽업완료 상담은 판매자 조회로 상담중 상태로 역행하지 않는다")
  void doesNotMovePaidOrPickedUpInquiryBackToInProgress() {
    Inquiry paidInquiry = Inquiry.create(UUID.randomUUID(), UUID.randomUUID());
    Inquiry pickedUpInquiry = Inquiry.create(UUID.randomUUID(), UUID.randomUUID());

    paidInquiry.markPaid();
    pickedUpInquiry.markPickedUp();
    paidInquiry.markInProgressOnSellerReview();
    pickedUpInquiry.markInProgressOnSellerReview();

    assertEquals(InquiryStatus.PAID, paidInquiry.getStatus());
    assertEquals(InquiryStatus.PICKED_UP, pickedUpInquiry.getStatus());
  }

  @Test
  @DisplayName("주문서 제출 자동 복구는 판매자 휴지통 플래그를 지우고 접수대기로 전환한다")
  void reopensSellerTrashOnOrderFormSubmission() {
    Inquiry inquiry = Inquiry.create(UUID.randomUUID(), UUID.randomUUID());
    Instant deletedAt = Instant.parse("2026-08-25T00:00:00Z");
    Instant purgedAt = Instant.parse("2026-09-25T00:00:00Z");

    inquiry.markInProgressOnSellerReview();
    inquiry.moveSellerToTrash(deletedAt);
    ReflectionTestUtils.setField(inquiry, "sellerPurgedAt", purgedAt);

    inquiry.reopenSellerOnSubmission();

    assertNull(inquiry.getSellerDeletedAt());
    assertNull(inquiry.getSellerPurgedAt());
    assertEquals(InquiryStatus.WAITING, inquiry.getStatus());
    assertEquals(InquiryStatus.WAITING, inquiry.statusForSeller());
  }

  @Test
  @DisplayName("판매자 수동 복구는 기존 내부 상담 상태를 유지한다")
  void keepsInternalStatusOnManualSellerRestore() {
    Inquiry inProgressInquiry = Inquiry.create(UUID.randomUUID(), UUID.randomUUID());
    Inquiry paidInquiry = Inquiry.create(UUID.randomUUID(), UUID.randomUUID());
    Instant deletedAt = Instant.parse("2026-08-25T00:00:00Z");

    inProgressInquiry.markInProgressOnSellerReview();
    paidInquiry.markPaid();
    inProgressInquiry.moveSellerToTrash(deletedAt);
    paidInquiry.moveSellerToTrash(deletedAt);

    inProgressInquiry.restoreSellerFromTrash();
    paidInquiry.restoreSellerFromTrash();

    assertEquals(InquiryStatus.IN_PROGRESS, inProgressInquiry.getStatus());
    assertEquals(InquiryStatus.IN_PROGRESS, inProgressInquiry.statusForSeller());
    assertEquals(InquiryStatus.PAID, paidInquiry.getStatus());
    assertEquals(InquiryStatus.PAID, paidInquiry.statusForSeller());
  }

  @Test
  @DisplayName("자동 비우기된 판매자 휴지통 상담은 기존처럼 수동 복구할 수 없다")
  void rejectsManualSellerRestoreAfterPurge() {
    Inquiry inquiry = Inquiry.create(UUID.randomUUID(), UUID.randomUUID());
    Instant deletedAt = Instant.parse("2026-08-25T00:00:00Z");
    Instant purgedAt = Instant.parse("2026-09-25T00:00:00Z");

    inquiry.moveSellerToTrash(deletedAt);
    ReflectionTestUtils.setField(inquiry, "sellerPurgedAt", purgedAt);

    assertThrows(IllegalStateException.class, inquiry::restoreSellerFromTrash);
  }
}
