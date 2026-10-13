package io.point3.p3api.chat.application.port;

import java.time.Instant;
import java.util.UUID;

public record UnreadCountTarget(UUID inquiryId, Instant readAt) {}
