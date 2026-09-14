package io.point3.p3api.inquiry.domain.entity;

import io.point3.p3api.inquiry.domain.type.InquiryStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "inquiries",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_inquiries_store_buyer",
            columnNames = {"store_id", "buyer_user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "store_id", nullable = false)
  private UUID storeId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private InquiryStatus status;

  @Column(name = "buyer_user_id", nullable = false)
  private UUID buyerUserId;

  @Column(name = "buyer_last_read_at")
  private Instant buyerLastReadAt;

  @Column(name = "seller_last_read_at")
  private Instant sellerLastReadAt;

  @Column(name = "buyer_deleted_at")
  private Instant buyerDeletedAt;

  @Column(name = "seller_deleted_at")
  private Instant sellerDeletedAt;

  @Column(name = "buyer_purged_at")
  private Instant buyerPurgedAt;

  @Column(name = "seller_purged_at")
  private Instant sellerPurgedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  private Inquiry(UUID storeId, UUID buyerUserId) {
    this.storeId = storeId;
    this.buyerUserId = buyerUserId;
    this.status = InquiryStatus.WAITING;
  }

  public static Inquiry create(UUID storeId, UUID buyerUserId) {
    Objects.requireNonNull(storeId, "storeId");
    Objects.requireNonNull(buyerUserId, "buyerUserId");

    return new Inquiry(storeId, buyerUserId);
  }

  public void markWaiting() {
    this.status = InquiryStatus.WAITING;
  }

  public void reopenSellerOnSubmission() {
    this.sellerDeletedAt = null;
    this.sellerPurgedAt = null;
    this.status = InquiryStatus.WAITING;
  }

  public void markInProgressOnSellerReview() {
    if (isPaidOrPickedUp()) {
      return;
    }

    this.status = InquiryStatus.IN_PROGRESS;
  }

  public void markPaid() {
    this.status = InquiryStatus.PAID;
  }

  public void markPickedUp() {
    this.status = InquiryStatus.PICKED_UP;
  }

  private boolean isPaidOrPickedUp() {
    return status == InquiryStatus.PAID || status == InquiryStatus.PICKED_UP;
  }

  public void markBuyerRead(Instant readAt) {
    this.buyerLastReadAt = Objects.requireNonNull(readAt, "readAt");
  }

  public void markSellerRead(Instant readAt) {
    this.sellerLastReadAt = Objects.requireNonNull(readAt, "readAt");
  }

  public void moveBuyerToTrash(Instant deletedAt) {
    this.buyerDeletedAt = Objects.requireNonNull(deletedAt, "deletedAt");
    this.buyerPurgedAt = null;
  }

  public void moveSellerToTrash(Instant deletedAt) {
    this.sellerDeletedAt = Objects.requireNonNull(deletedAt, "deletedAt");
    this.sellerPurgedAt = null;
  }

  public void restoreBuyerFromTrash() {
    if (buyerPurgedAt != null) {
      throw new IllegalStateException("buyer trash is already emptied");
    }

    this.buyerDeletedAt = null;
  }

  public void restoreSellerFromTrash() {
    if (sellerPurgedAt != null) {
      throw new IllegalStateException("seller trash is already emptied");
    }

    this.sellerDeletedAt = null;
  }

  public InquiryStatus statusForBuyer() {
    return isBuyerTrashed() ? InquiryStatus.TRASH : status;
  }

  public InquiryStatus statusForSeller() {
    return isSellerTrashed() ? InquiryStatus.TRASH : status;
  }

  public boolean isBuyerVisible() {
    return buyerPurgedAt == null;
  }

  public boolean isSellerVisible() {
    return sellerPurgedAt == null;
  }

  public boolean isBuyerTrashed() {
    return buyerDeletedAt != null && buyerPurgedAt == null;
  }

  public boolean isSellerTrashed() {
    return sellerDeletedAt != null && sellerPurgedAt == null;
  }
}
