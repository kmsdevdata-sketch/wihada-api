package io.point3.p3api.notification.domain.entity;

import io.point3.p3api.notification.domain.type.EmailNotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "seller_email_notification_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerEmailNotificationLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "seller_user_id", nullable = false)
  private UUID sellerUserId;

  @Column(name = "inquiry_id", nullable = false)
  private UUID inquiryId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 50)
  private EmailNotificationType type;

  @Column(name = "sent_at", nullable = false)
  private Instant sentAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  private SellerEmailNotificationLog(
      UUID sellerUserId, UUID inquiryId, EmailNotificationType type, Instant sentAt) {
    this.sellerUserId = sellerUserId;
    this.inquiryId = inquiryId;
    this.type = type;
    this.sentAt = sentAt;
  }

  public static SellerEmailNotificationLog create(
      UUID sellerUserId, UUID inquiryId, EmailNotificationType type, Instant sentAt) {
    Objects.requireNonNull(sellerUserId, "sellerUserId");
    Objects.requireNonNull(inquiryId, "inquiryId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(sentAt, "sentAt");

    return new SellerEmailNotificationLog(sellerUserId, inquiryId, type, sentAt);
  }
}
