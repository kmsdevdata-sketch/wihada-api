package io.point3.p3api.inquiry.application.list;

import io.point3.p3api.chat.application.port.ChatMessagePort;
import io.point3.p3api.chat.application.port.ChatTimelineItemPort;
import io.point3.p3api.chat.application.port.UnreadCountTarget;
import io.point3.p3api.chat.domain.entity.ChatTimelineItem;
import io.point3.p3api.chat.domain.type.ChatTimelineItemType;
import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.ChatErrorCode;
import io.point3.p3api.inquiry.application.chat.InquiryChatAccessService;
import io.point3.p3api.inquiry.application.chat.InquiryChatDetailQueryUseCase;
import io.point3.p3api.inquiry.application.port.InquiryPersistencePort;
import io.point3.p3api.inquiry.application.port.OrderFormSubmissionPersistencePort;
import io.point3.p3api.inquiry.application.realtime.InquiryListChangeEventPublisher;
import io.point3.p3api.inquiry.application.result.InquiryChatDetail;
import io.point3.p3api.inquiry.application.result.InquiryListItem;
import io.point3.p3api.inquiry.domain.entity.Inquiry;
import io.point3.p3api.inquiry.domain.entity.OrderFormSubmission;
import io.point3.p3api.inquiry.domain.type.InquiryStatus;
import io.point3.p3api.store.application.port.StorePersistencePort;
import io.point3.p3api.store.domain.entity.Store;
import io.point3.p3api.user.application.port.UserPersistencePort;
import io.point3.p3api.user.application.profile.ProfileImageDeliveryService;
import io.point3.p3api.user.domain.entity.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class InquiryListService implements InquiryListUseCase {
  private final InquiryPersistencePort inquiryPersistencePort;
  private final InquiryChatAccessService inquiryChatAccessService;
  private final InquiryChatDetailQueryUseCase inquiryChatDetailQueryUseCase;
  private final ChatMessagePort chatMessagePort;
  private final ChatTimelineItemPort chatTimelineItemPort;
  private final OrderFormSubmissionPersistencePort orderFormSubmissionPersistencePort;
  private final StorePersistencePort storePersistencePort;
  private final UserPersistencePort userPersistencePort;
  private final ProfileImageDeliveryService profileImageDeliveryService;
  private final InquiryListChangeEventPublisher inquiryListChangeEventPublisher;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public List<InquiryListItem> getBuyerInquiries(UUID buyerUserId, InquiryStatus status) {
    return getBuyerInquiries(buyerUserId, status, false);
  }

  @Override
  @Transactional(readOnly = true)
  public List<InquiryListItem> getBuyerInquiries(
      UUID buyerUserId, InquiryStatus status, boolean unreadOnly) {
    List<Inquiry> inquiries = inquiryPersistencePort.findAllByBuyerUserId(buyerUserId).stream()
        .filter(inquiry -> isBuyerListTarget(inquiry, status))
        .toList();
    Map<UUID, InquiryListItem.LatestEvent> latestEventByInquiryId =
        latestEventByInquiryId(inquiries);
    Map<UUID, InquiryListItem.LatestOrderFormSubmission> latestSubmissionByInquiryId =
        latestSubmissionByInquiryId(inquiries);

    return inquiries.stream()
        .map(inquiry ->
            toBuyerItem(inquiry, buyerUserId, latestEventByInquiryId, latestSubmissionByInquiryId))
        .filter(item -> !unreadOnly || item.unreadCount() > 0)
        .sorted(byLatestEvent())
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<InquiryListItem> getSellerInquiries(
      UUID storeId, UUID sellerUserId, InquiryStatus status) {
    return getSellerInquiries(storeId, sellerUserId, status, false);
  }

  @Override
  @Transactional(readOnly = true)
  public List<InquiryListItem> getSellerInquiries(
      UUID storeId, UUID sellerUserId, InquiryStatus status, boolean unreadOnly) {
    List<Inquiry> inquiries = inquiryPersistencePort.findAllByStoreId(storeId).stream()
        .filter(inquiry -> isSellerListTarget(inquiry, status))
        .toList();
    Store store = inquiries.isEmpty() ? null : findStore(storeId);
    Map<UUID, User> buyersById = buyersById(inquiries);
    Map<UUID, String> profileImageDeliveryUrlByUserId =
        profileImageDeliveryService.resolveByUserId(buyersById.values().stream().toList());
    Map<UUID, InquiryListItem.LatestEvent> latestEventByInquiryId =
        latestEventByInquiryId(inquiries);
    Map<UUID, InquiryListItem.LatestOrderFormSubmission> latestSubmissionByInquiryId =
        latestSubmissionByInquiryId(inquiries);

    Map<UUID, Long> unreadCountsByInquiryId = chatTimelineItemPort.countUnread(
        inquiries.stream()
            .map(inquiry -> new UnreadCountTarget(inquiry.getId(), inquiry.getSellerLastReadAt()))
            .toList(),
        sellerUserId);

    return inquiries.stream()
        .map(inquiry -> toSellerItem(
            inquiry,
            store,
            buyersById,
            profileImageDeliveryUrlByUserId,
            latestEventByInquiryId,
            latestSubmissionByInquiryId,
            unreadCountsByInquiryId))
        .filter(item -> !unreadOnly || item.unreadCount() > 0)
        .sorted(byLatestEvent())
        .toList();
  }

  @Override
  public void markBuyerRead(UUID inquiryId, UUID buyerUserId) {
    inquiryChatAccessService
        .getBuyerInquiry(inquiryId, buyerUserId)
        .markBuyerRead(Instant.now(clock));
    inquiryListChangeEventPublisher.publishReaderChanged(inquiryId, buyerUserId);
  }

  @Override
  public void markSellerRead(UUID inquiryId, UUID storeId) {
    Inquiry inquiry = inquiryChatAccessService.getSellerInquiry(inquiryId, storeId);
    inquiry.markSellerRead(Instant.now(clock));
    publishSellerChanged(inquiryId, storeId);
  }

  @Override
  public void moveBuyerToTrash(UUID inquiryId, UUID buyerUserId) {
    Inquiry inquiry = inquiryChatAccessService.getBuyerInquiry(inquiryId, buyerUserId);
    ensureBuyerVisible(inquiry);
    inquiry.moveBuyerToTrash(Instant.now(clock));
    inquiryListChangeEventPublisher.publishReaderChanged(inquiryId, buyerUserId);
  }

  @Override
  public void moveSellerToTrash(UUID inquiryId, UUID storeId) {
    Inquiry inquiry = inquiryChatAccessService.getSellerInquiry(inquiryId, storeId);
    ensureSellerVisible(inquiry);
    inquiry.moveSellerToTrash(Instant.now(clock));
    publishSellerChanged(inquiryId, storeId);
  }

  @Override
  public void restoreBuyerFromTrash(UUID inquiryId, UUID buyerUserId) {
    Inquiry inquiry = inquiryChatAccessService.getBuyerInquiry(inquiryId, buyerUserId);
    ensureBuyerVisible(inquiry);
    inquiry.restoreBuyerFromTrash();
    inquiryListChangeEventPublisher.publishReaderChanged(inquiryId, buyerUserId);
  }

  @Override
  public void restoreSellerFromTrash(UUID inquiryId, UUID storeId) {
    Inquiry inquiry = inquiryChatAccessService.getSellerInquiry(inquiryId, storeId);
    ensureSellerVisible(inquiry);
    inquiry.restoreSellerFromTrash();
    publishSellerChanged(inquiryId, storeId);
  }

  @Override
  @Scheduled(cron = "0 0 3 * * *")
  public void purgeExpiredTrash() {
    Instant now = Instant.now(clock);
    Instant cutoff = ZonedDateTime.now(clock).minusMonths(1).toInstant();
    inquiryPersistencePort.purgeExpiredTrash(cutoff, now);
  }

  private InquiryListItem toBuyerItem(
      Inquiry inquiry,
      UUID buyerUserId,
      Map<UUID, InquiryListItem.LatestEvent> latestEventByInquiryId,
      Map<UUID, InquiryListItem.LatestOrderFormSubmission> latestSubmissionByInquiryId) {
    InquiryChatDetail detail = inquiryChatDetailQueryUseCase.getBuyerDetail(inquiry);
    return toItem(
        inquiry,
        buyerUserId,
        inquiry.getBuyerLastReadAt(),
        detail,
        inquiry.statusForBuyer(),
        latestEventByInquiryId.get(inquiry.getId()),
        latestSubmissionByInquiryId.get(inquiry.getId()));
  }

  private InquiryListItem toSellerItem(
      Inquiry inquiry,
      Store store,
      Map<UUID, User> buyersById,
      Map<UUID, String> profileImageDeliveryUrlByUserId,
      Map<UUID, InquiryListItem.LatestEvent> latestEventByInquiryId,
      Map<UUID, InquiryListItem.LatestOrderFormSubmission> latestSubmissionByInquiryId,
      Map<UUID, Long> unreadCountsByInquiryId) {
    User buyer = findBuyer(inquiry, buyersById);
    InquiryChatDetail detail = InquiryChatDetail.of(
        inquiry,
        store,
        new InquiryChatDetail.Participant(
            buyer.getId(),
            buyer.getName(),
            buyer.getPhoneNumber(),
            profileImageDeliveryUrlByUserId.get(buyer.getId())),
        null,
        inquiry.getSellerLastReadAt(),
        inquiry.getBuyerLastReadAt());
    InquiryListItem.LatestEvent latestEvent = latestEventByInquiryId.get(inquiry.getId());
    return InquiryListItem.from(
        inquiry,
        detail,
        inquiry.statusForSeller(),
        unreadCountsByInquiryId.getOrDefault(inquiry.getId(), 0L),
        latestEvent == null ? null : latestEvent.createdAt(),
        latestEvent,
        latestSubmissionByInquiryId.get(inquiry.getId()));
  }

  private InquiryListItem toItem(
      Inquiry inquiry,
      UUID readerUserId,
      Instant readAt,
      InquiryChatDetail detail,
      InquiryStatus status,
      InquiryListItem.LatestEvent latestEvent,
      InquiryListItem.LatestOrderFormSubmission latestOrderFormSubmission) {
    long unreadCount = chatTimelineItemPort.countUnread(inquiry.getId(), readerUserId, readAt);
    Instant latestEventAt = latestEvent == null ? null : latestEvent.createdAt();
    return InquiryListItem.from(
        inquiry,
        detail,
        status,
        unreadCount,
        latestEventAt,
        latestEvent,
        latestOrderFormSubmission);
  }

  private Map<UUID, InquiryListItem.LatestEvent> latestEventByInquiryId(List<Inquiry> inquiries) {
    List<ChatTimelineItem> latestItems =
        chatTimelineItemPort.findLatestByInquiryIds(inquiryIds(inquiries));
    Map<UUID, String> messageContentById = messageContentById(latestItems);

    return latestItems.stream()
        .collect(Collectors.toMap(
            ChatTimelineItem::getInquiryId,
            item -> latestEvent(item, messageContentById),
            this::latestEvent));
  }

  private Map<UUID, String> messageContentById(List<ChatTimelineItem> latestItems) {
    List<UUID> messageIds = latestItems.stream()
        .filter(item -> item.getType() == ChatTimelineItemType.MESSAGE)
        .map(ChatTimelineItem::getReferenceId)
        .distinct()
        .toList();

    Map<UUID, String> messageContentById = new HashMap<>();
    chatMessagePort
        .findAllById(messageIds)
        .forEach(message -> messageContentById.put(message.getId(), message.getContent()));
    return messageContentById;
  }

  private InquiryListItem.LatestEvent latestEvent(
      ChatTimelineItem item, Map<UUID, String> messageContentById) {
    return new InquiryListItem.LatestEvent(
        item.getId(),
        item.getReferenceId(),
        item.getType(),
        item.getSenderUserId(),
        messageContentById.get(item.getReferenceId()),
        item.getCreatedAt());
  }

  private InquiryListItem.LatestEvent latestEvent(
      InquiryListItem.LatestEvent first, InquiryListItem.LatestEvent second) {
    if (first.createdAt().equals(second.createdAt())) {
      return first.eventId().compareTo(second.eventId()) >= 0 ? first : second;
    }
    return first.createdAt().isAfter(second.createdAt()) ? first : second;
  }

  private Map<UUID, InquiryListItem.LatestOrderFormSubmission> latestSubmissionByInquiryId(
      List<Inquiry> inquiries) {
    return orderFormSubmissionPersistencePort.findLatestByInquiryIds(inquiryIds(inquiries)).stream()
        .collect(Collectors.toMap(
            OrderFormSubmission::getInquiryId,
            submission -> new InquiryListItem.LatestOrderFormSubmission(
                submission.getId(), submission.getSubmittedAt()),
            this::latestSubmission));
  }

  private InquiryListItem.LatestOrderFormSubmission latestSubmission(
      InquiryListItem.LatestOrderFormSubmission first,
      InquiryListItem.LatestOrderFormSubmission second) {
    if (first.submittedAt().equals(second.submittedAt())) {
      return first.submissionId().compareTo(second.submissionId()) >= 0 ? first : second;
    }
    return first.submittedAt().isAfter(second.submittedAt()) ? first : second;
  }

  private List<UUID> inquiryIds(List<Inquiry> inquiries) {
    return inquiries.stream().map(Inquiry::getId).toList();
  }

  private Store findStore(UUID storeId) {
    return storePersistencePort
        .findById(storeId)
        .orElseThrow(() -> new BaseException(ChatErrorCode.CHAT_INQUIRY_NOT_FOUND));
  }

  private void publishSellerChanged(UUID inquiryId, UUID storeId) {
    inquiryListChangeEventPublisher.publishReaderChanged(
        inquiryId, findStore(storeId).getOwnerUserId());
  }

  private Map<UUID, User> buyersById(List<Inquiry> inquiries) {
    List<UUID> buyerUserIds =
        inquiries.stream().map(Inquiry::getBuyerUserId).distinct().toList();
    Map<UUID, User> buyersById = new HashMap<>();
    userPersistencePort
        .findAllById(buyerUserIds)
        .forEach(user -> buyersById.put(user.getId(), user));
    return buyersById;
  }

  private User findBuyer(Inquiry inquiry, Map<UUID, User> buyersById) {
    User buyer = buyersById.get(inquiry.getBuyerUserId());
    if (buyer == null) {
      throw new BaseException(ChatErrorCode.CHAT_INQUIRY_NOT_FOUND);
    }
    return buyer;
  }

  private boolean isBuyerListTarget(Inquiry inquiry, InquiryStatus status) {
    if (!inquiry.isBuyerVisible()) {
      return false;
    }

    if (status == InquiryStatus.TRASH) {
      return inquiry.isBuyerTrashed();
    }

    return !inquiry.isBuyerTrashed() && (status == null || inquiry.getStatus() == status);
  }

  private boolean isSellerListTarget(Inquiry inquiry, InquiryStatus status) {
    if (!inquiry.isSellerVisible()) {
      return false;
    }

    if (status == InquiryStatus.TRASH) {
      return inquiry.isSellerTrashed();
    }

    return !inquiry.isSellerTrashed() && (status == null || inquiry.getStatus() == status);
  }

  private void ensureBuyerVisible(Inquiry inquiry) {
    if (!inquiry.isBuyerVisible()) {
      throw new BaseException(ChatErrorCode.CHAT_INQUIRY_NOT_FOUND);
    }
  }

  private void ensureSellerVisible(Inquiry inquiry) {
    if (!inquiry.isSellerVisible()) {
      throw new BaseException(ChatErrorCode.CHAT_INQUIRY_NOT_FOUND);
    }
  }

  private Comparator<InquiryListItem> byLatestEvent() {
    return Comparator.comparing(InquiryListItem::latestEventAt).reversed();
  }
}
