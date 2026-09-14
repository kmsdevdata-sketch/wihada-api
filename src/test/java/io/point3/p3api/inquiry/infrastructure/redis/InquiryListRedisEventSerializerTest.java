package io.point3.p3api.inquiry.infrastructure.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.point3.p3api.inquiry.application.realtime.InquiryListRealtimePayload;
import io.point3.p3api.inquiry.domain.type.InquiryStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InquiryListRedisEventSerializerTest {

  private final InquiryListRedisEventSerializer serializer =
      new InquiryListRedisEventSerializer(new ObjectMapper().findAndRegisterModules());

  @Test
  @DisplayName("Instant를 포함한 문의 목록 Redis 이벤트를 직렬화하고 원본대로 역직렬화한다")
  void serializesAndDeserializesEventIncludingInstant() {
    InquiryListRedisEvent event = new InquiryListRedisEvent(
        UUID.randomUUID(),
        InquiryListRealtimePayload.inquiryUpdated(
            UUID.randomUUID(),
            3,
            Instant.parse("2026-09-07T00:00:00Z"),
            InquiryStatus.WAITING,
            UUID.randomUUID()));

    String serialized = serializer.serialize(event);
    InquiryListRedisEvent deserialized =
        serializer.deserialize(serialized.getBytes(StandardCharsets.UTF_8));

    assertEquals(event, deserialized);
  }

  @Test
  @DisplayName("형식이 잘못된 Redis payload는 IllegalArgumentException으로 변환한다")
  void throwsIllegalArgumentExceptionForMalformedPayload() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> serializer.deserialize("not-json".getBytes(StandardCharsets.UTF_8)));

    assertInstanceOf(JsonProcessingException.class, exception.getCause());
  }
}
