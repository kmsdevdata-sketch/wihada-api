package io.point3.p3api.notification.infrastructure.persistence;

import io.point3.p3api.notification.application.port.SellerEmailNotificationLogPort;
import io.point3.p3api.notification.domain.entity.SellerEmailNotificationLog;
import io.point3.p3api.notification.domain.type.EmailNotificationType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
@RequiredArgsConstructor
public class SellerEmailNotificationLogAdapter implements SellerEmailNotificationLogPort {

  private final SellerEmailNotificationLogJpaRepository sellerEmailNotificationLogJpaRepository;

  @Override
  @Transactional(readOnly = true)
  public boolean existsByInquiryIdAndType(UUID inquiryId, EmailNotificationType type) {
    return sellerEmailNotificationLogJpaRepository.existsByInquiryIdAndType(inquiryId, type);
  }

  @Override
  public SellerEmailNotificationLog save(SellerEmailNotificationLog log) {
    return sellerEmailNotificationLogJpaRepository.save(log);
  }
}
