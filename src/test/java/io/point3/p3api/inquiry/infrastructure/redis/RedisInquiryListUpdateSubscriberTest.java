package io.point3.p3api.inquiry.infrastructure.redis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.point3.p3api.inquiry.application.realtime.InquiryListRealtimePayload;
import io.point3.p3api.inquiry.controller.InquiryListStompDestination;
import io.point3.p3api.inquiry.domain.type.InquiryStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class RedisInquiryListUpdateSubscriberTest {

  @Test
  @DisplayName("잘못된 Redis payload를 받아도 리스너 예외를 전파하지 않는다")
  void doesNotPropagateExceptionForMalformedRedisPayload() {
    InquiryListRedisEventSerializer eventSerializer = mock(InquiryListRedisEventSerializer.class);
    SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    Message message = mock(Message.class);
    when(message.getBody()).thenReturn("invalid".getBytes(StandardCharsets.UTF_8));
    when(eventSerializer.deserialize(message.getBody()))
        .thenThrow(new IllegalArgumentException("Invalid Redis payload"));
    RedisInquiryListUpdateSubscriber subscriber =
        new RedisInquiryListUpdateSubscriber(eventSerializer, messagingTemplate);

    assertDoesNotThrow(() -> subscriber.onMessage(message, null));

    verifyNoInteractions(messagingTemplate);
  }

  @Test
  @DisplayName("Redis 문의 목록 갱신 이벤트를 사용자 STOMP 토픽으로 전달한다")
  void forwardsUpdateToUserStompTopic() {
    InquiryListRedisEvent event = event();
    InquiryListRedisEventSerializer eventSerializer = mock(InquiryListRedisEventSerializer.class);
    SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    Message message = mock(Message.class);
    when(message.getBody()).thenReturn("payload".getBytes(StandardCharsets.UTF_8));
    when(eventSerializer.deserialize(message.getBody())).thenReturn(event);
    String destination = InquiryListStompDestination.topicDestination(event.userId());
    doThrow(new IllegalStateException("STOMP broker is unavailable"))
        .doNothing()
        .when(messagingTemplate)
        .convertAndSend(destination, event.payload());
    RedisInquiryListUpdateSubscriber subscriber =
        new RedisInquiryListUpdateSubscriber(eventSerializer, messagingTemplate);

    assertDoesNotThrow(() -> subscriber.onMessage(message, null));
    assertDoesNotThrow(() -> subscriber.onMessage(message, null));

    verify(messagingTemplate, times(2)).convertAndSend(destination, event.payload());
  }

  private InquiryListRedisEvent event() {
    return new InquiryListRedisEvent(
        UUID.randomUUID(),
        InquiryListRealtimePayload.inquiryUpdated(
            UUID.randomUUID(),
            1,
            Instant.parse("2026-09-07T00:00:00Z"),
            InquiryStatus.WAITING,
            UUID.randomUUID()));
  }
}
