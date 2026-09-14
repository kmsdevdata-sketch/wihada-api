package io.point3.p3api.notification.application.port;

import io.point3.p3api.notification.domain.entity.SellerEmailNotificationLog;
import io.point3.p3api.notification.domain.type.EmailNotificationType;
import java.util.UUID;

public interface SellerEmailNotificationLogPort {
  boolean existsByInquiryIdAndType(UUID inquiryId, EmailNotificationType type);

  SellerEmailNotificationLog save(SellerEmailNotificationLog log);
}
