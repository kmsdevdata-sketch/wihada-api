package io.point3.p3api.inquiry.infrastructure.redis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.point3.p3api.inquiry.application.realtime.InquiryListRealtimeEvent;
import io.point3.p3api.inquiry.application.realtime.InquiryListRealtimePayload;
import io.point3.p3api.inquiry.domain.type.InquiryStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisInquiryListUpdatePublisherTest {

  @Test
  @DisplayName("Redis 발행이 실패해도 예외를 전파하지 않는다")
  void doesNotPropagateExceptionWhenRedisPublishingFails() {
    StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
    InquiryListRedisEventSerializer eventSerializer = mock(InquiryListRedisEventSerializer.class);
    when(eventSerializer.serialize(any(InquiryListRedisEvent.class))).thenReturn("payload");
    doThrow(new IllegalStateException("Redis is unavailable"))
        .when(stringRedisTemplate)
        .convertAndSend(InquiryListRedisChannel.UPDATES, "payload");
    RedisInquiryListUpdatePublisher publisher =
        new RedisInquiryListUpdatePublisher(stringRedisTemplate, eventSerializer);

    assertDoesNotThrow(() -> publisher.publish(event()));

    verify(stringRedisTemplate).convertAndSend(eq(InquiryListRedisChannel.UPDATES), eq("payload"));
  }

  private InquiryListRealtimeEvent event() {
    return new InquiryListRealtimeEvent(
        UUID.randomUUID(),
        InquiryListRealtimePayload.inquiryUpdated(
            UUID.randomUUID(),
            1,
            Instant.parse("2026-09-07T00:00:00Z"),
            InquiryStatus.WAITING,
            UUID.randomUUID()));
  }
}
