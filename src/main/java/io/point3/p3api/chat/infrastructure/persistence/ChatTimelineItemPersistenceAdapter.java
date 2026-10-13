package io.point3.p3api.chat.infrastructure.persistence;

import io.point3.p3api.chat.application.port.ChatTimelineItemPort;
import io.point3.p3api.chat.application.port.UnreadCountTarget;
import io.point3.p3api.chat.domain.entity.ChatTimelineItem;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatTimelineItemPersistenceAdapter implements ChatTimelineItemPort {

  private final ChatTimelineItemJpaRepository chatTimelineItemJpaRepository;
  private final JdbcTemplate jdbcTemplate;

  @Override
  public ChatTimelineItem save(ChatTimelineItem chatTimelineItem) {
    return chatTimelineItemJpaRepository.save(chatTimelineItem);
  }

  @Override
  public Optional<ChatTimelineItem> findById(UUID eventId) {
    return chatTimelineItemJpaRepository.findById(eventId);
  }

  @Override
  public List<ChatTimelineItem> findTimeline(
      UUID inquiryId, Instant cursorCreatedAt, UUID cursorId, int limit) {
    if (cursorCreatedAt == null) {
      return chatTimelineItemJpaRepository.findTimeline(inquiryId, PageRequest.of(0, limit));
    }

    return chatTimelineItemJpaRepository.findTimeline(
        inquiryId, cursorCreatedAt, cursorId, PageRequest.of(0, limit));
  }

  @Override
  public long countUnread(UUID inquiryId, UUID readerUserId, Instant readAt) {
    if (readAt == null) {
      return chatTimelineItemJpaRepository.countUnread(inquiryId, readerUserId);
    }

    return chatTimelineItemJpaRepository.countUnread(inquiryId, readerUserId, readAt);
  }

  @Override
  public Map<UUID, Long> countUnread(List<UnreadCountTarget> targets, UUID readerUserId) {
    if (targets.isEmpty()) {
      return Map.of();
    }

    String values = String.join(
        ", ", java.util.Collections.nCopies(targets.size(), "(?::uuid, ?::timestamptz)"));
    String sql = """
        with targets(inquiry_id, read_at) as (values %s)
        select targets.inquiry_id, count(item.id) as unread_count
        from targets
        left join chat_timeline_items item
          on item.inquiry_id = targets.inquiry_id
         and item.sender_user_id <> ?::uuid
         and (targets.read_at is null or item.created_at > targets.read_at)
        group by targets.inquiry_id
        """.formatted(values);

    return jdbcTemplate.query(
        sql,
        statement -> {
          int parameterIndex = 1;
          for (UnreadCountTarget target : targets) {
            statement.setObject(parameterIndex++, target.inquiryId());
            if (target.readAt() == null) {
              statement.setNull(parameterIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
              statement.setObject(parameterIndex++, target.readAt().atOffset(ZoneOffset.UTC));
            }
          }
          statement.setObject(parameterIndex, readerUserId);
        },
        rows -> {
          Map<UUID, Long> countsByInquiryId = new HashMap<>();
          while (rows.next()) {
            countsByInquiryId.put(
                rows.getObject("inquiry_id", UUID.class), rows.getLong("unread_count"));
          }
          return countsByInquiryId;
        });
  }

  @Override
  public Instant findLatestCreatedAt(UUID inquiryId) {
    return chatTimelineItemJpaRepository.findLatestCreatedAt(inquiryId);
  }

  @Override
  public List<ChatTimelineItem> findLatestByInquiryIds(List<UUID> inquiryIds) {
    if (inquiryIds.isEmpty()) {
      return List.of();
    }
    return chatTimelineItemJpaRepository.findLatestByInquiryIds(inquiryIds);
  }
}
