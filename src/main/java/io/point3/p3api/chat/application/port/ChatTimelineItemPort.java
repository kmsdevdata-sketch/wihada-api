package io.point3.p3api.chat.application.port;

import io.point3.p3api.chat.domain.entity.ChatTimelineItem;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ChatTimelineItemPort {

  ChatTimelineItem save(ChatTimelineItem chatTimelineItem);

  Optional<ChatTimelineItem> findById(UUID eventId);

  List<ChatTimelineItem> findTimeline(
      UUID inquiryId, Instant cursorCreatedAt, UUID cursorId, int limit);

  long countUnread(UUID inquiryId, UUID readerUserId, Instant readAt);

  Map<UUID, Long> countUnread(List<UnreadCountTarget> targets, UUID readerUserId);

  Instant findLatestCreatedAt(UUID inquiryId);

  List<ChatTimelineItem> findLatestByInquiryIds(List<UUID> inquiryIds);
}
