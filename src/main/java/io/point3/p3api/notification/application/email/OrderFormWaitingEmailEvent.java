package io.point3.p3api.notification.application.email;

import java.util.UUID;

public record OrderFormWaitingEmailEvent(UUID sellerUserId, UUID inquiryId, UUID submissionId) {}
