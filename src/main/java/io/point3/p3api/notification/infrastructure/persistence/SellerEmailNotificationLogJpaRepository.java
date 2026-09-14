package io.point3.p3api.notification.infrastructure.persistence;

import io.point3.p3api.notification.domain.entity.SellerEmailNotificationLog;
import io.point3.p3api.notification.domain.type.EmailNotificationType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerEmailNotificationLogJpaRepository
    extends JpaRepository<SellerEmailNotificationLog, UUID> {

  boolean existsByInquiryIdAndType(UUID inquiryId, EmailNotificationType type);
}
