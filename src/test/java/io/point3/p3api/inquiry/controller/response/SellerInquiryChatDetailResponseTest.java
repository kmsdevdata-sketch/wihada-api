package io.point3.p3api.inquiry.controller.response;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.point3.p3api.inquiry.application.result.InquiryChatDetail;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SellerInquiryChatDetailResponseTest {

  @Test
  @DisplayName("판매자 문의 상세 응답은 구매자 연락처를 포함한다")
  void includesBuyerPhoneNumber() {
    InquiryChatDetail detail = new InquiryChatDetail(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "테스트 스토어",
        "test-store",
        new InquiryChatDetail.Participant(
            UUID.randomUUID(), "구매자", "010-1234-5678", "https://example.com/profile.webp"),
        null,
        UUID.randomUUID(),
        Instant.parse("2026-09-09T10:00:00Z"),
        Instant.parse("2026-09-09T09:00:00Z"),
        Instant.parse("2026-09-09T08:00:00Z"));

    SellerInquiryChatDetailResponse response = SellerInquiryChatDetailResponse.from(detail);

    assertEquals("010-1234-5678", response.participant().phoneNumber());
  }
}
