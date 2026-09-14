package io.point3.p3api.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.point3.p3api.IntegrationTestSupport;
import io.point3.p3api.asset.domain.entity.Asset;
import io.point3.p3api.asset.infrastructure.persistence.AssetJpaRepository;
import io.point3.p3api.assetvariant.domain.entity.AssetVariant;
import io.point3.p3api.assetvariant.domain.type.AssetVariantType;
import io.point3.p3api.assetvariant.infrastructure.persistence.AssetVariantJpaRepository;
import io.point3.p3api.chat.application.timeline.ChatTimelineItemPublisher;
import io.point3.p3api.chat.application.timeline.query.ChatTimelineQuery;
import io.point3.p3api.chat.application.timeline.query.ChatTimelineQueryService;
import io.point3.p3api.chat.application.timeline.result.ChatTimelineItemResult;
import io.point3.p3api.chat.application.timeline.result.ChatTimelinePage;
import io.point3.p3api.chat.domain.entity.ChatTimelineItem;
import io.point3.p3api.chat.domain.type.ChatTimelineItemType;
import io.point3.p3api.chat.infrastructure.persistence.ChatTimelineItemJpaRepository;
import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.NotificationErrorCode;
import io.point3.p3api.exception.code.OrderErrorCode;
import io.point3.p3api.exception.code.OrderFormErrorCode;
import io.point3.p3api.gallery.domain.entity.StoreGalleryItem;
import io.point3.p3api.gallery.infrastructure.persistence.GalleryItemJpaRepository;
import io.point3.p3api.inquiry.application.chat.InquiryChatDetailQueryService;
import io.point3.p3api.inquiry.application.command.ConsumeOrderFormDraftCommand;
import io.point3.p3api.inquiry.application.command.CreateOrderFormDraftCommand;
import io.point3.p3api.inquiry.application.command.CreateOrderFormSubmissionCommand;
import io.point3.p3api.inquiry.application.command.OpenInquiryCommand;
import io.point3.p3api.inquiry.application.draft.consume.OrderFormDraftConsumeService;
import io.point3.p3api.inquiry.application.draft.create.OrderFormDraftService;
import io.point3.p3api.inquiry.application.draft.model.OrderFormDraftData;
import io.point3.p3api.inquiry.application.list.InquiryListService;
import io.point3.p3api.inquiry.application.open.InquiryOpenService;
import io.point3.p3api.inquiry.application.port.OrderFormDraftStorePort;
import io.point3.p3api.inquiry.application.result.InquiryChatDetail;
import io.point3.p3api.inquiry.application.result.InquiryListItem;
import io.point3.p3api.inquiry.application.result.OrderFormDraftConsumeResult;
import io.point3.p3api.inquiry.application.result.OrderFormDraftResult;
import io.point3.p3api.inquiry.application.submission.create.OrderFormSubmissionService;
import io.point3.p3api.inquiry.controller.response.ChatTimelineItemResponse;
import io.point3.p3api.inquiry.domain.entity.Inquiry;
import io.point3.p3api.inquiry.domain.entity.OrderFormSubmission;
import io.point3.p3api.inquiry.domain.type.InquiryStatus;
import io.point3.p3api.inquiry.domain.type.OrderFormReferenceAssetSource;
import io.point3.p3api.inquiry.infrastructure.persistence.InquiryJpaRepository;
import io.point3.p3api.notification.application.NotificationService;
import io.point3.p3api.notification.application.result.NotificationResult;
import io.point3.p3api.notification.domain.entity.Notification;
import io.point3.p3api.notification.domain.type.NotificationReferenceType;
import io.point3.p3api.notification.domain.type.NotificationType;
import io.point3.p3api.notification.infrastructure.persistence.NotificationJpaRepository;
import io.point3.p3api.order.application.OrderConfirmationService;
import io.point3.p3api.order.application.query.OrderConfirmationQueryService;
import io.point3.p3api.order.application.query.order.OrderQueryService;
import io.point3.p3api.order.application.result.SendOrderConfirmationResult;
import io.point3.p3api.order.application.send.SendOrderConfirmationCommand;
import io.point3.p3api.order.application.state.OrderConfirmationStateService;
import io.point3.p3api.order.domain.entity.Order;
import io.point3.p3api.order.domain.entity.OrderConfirmation;
import io.point3.p3api.order.domain.type.OrderConfirmationStatus;
import io.point3.p3api.order.infrastructure.persistence.OrderJpaRepository;
import io.point3.p3api.orderform.application.OrderFormOptionCommand;
import io.point3.p3api.orderform.application.OrderFormService;
import io.point3.p3api.orderform.application.create.CreateOrderFormCommand;
import io.point3.p3api.orderform.application.result.OrderFormResult;
import io.point3.p3api.orderform.domain.type.OptionInputType;
import io.point3.p3api.orderform.domain.type.OrderFormCategory;
import io.point3.p3api.orderform.domain.type.SelectionType;
import io.point3.p3api.payment.domain.entity.PaymentAttempt;
import io.point3.p3api.payment.infrastructure.persistence.PaymentAttemptJpaRepository;
import io.point3.p3api.store.application.StoreService;
import io.point3.p3api.store.application.create.CreateStoreCommand;
import io.point3.p3api.store.application.result.StoreResult;
import io.point3.p3api.store.application.setting.StoreSettingService;
import io.point3.p3api.store.application.setting.command.UpdateStoreSettingCommand;
import io.point3.p3api.user.domain.entity.User;
import io.point3.p3api.user.domain.type.SignupProvider;
import io.point3.p3api.user.domain.type.UserRole;
import io.point3.p3api.user.infrastructure.persistence.UserJpaRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

@TestPropertySource(properties = "p3.asset.delivery.base-url=https://assets.example.test")
class CoreApplicationWorkflowIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private StoreService storeService;

  @Autowired
  private StoreSettingService storeSettingService;

  @Autowired
  private OrderFormService orderFormService;

  @Autowired
  private InquiryOpenService inquiryOpenService;

  @Autowired
  private OrderFormSubmissionService submissionService;

  @Autowired
  private OrderConfirmationService orderConfirmationService;

  @Autowired
  private InquiryListService inquiryListService;

  @Autowired
  private InquiryChatDetailQueryService inquiryChatDetailQueryService;

  @Autowired
  private ChatTimelineQueryService chatTimelineQueryService;

  @Autowired
  private ChatTimelineItemPublisher chatTimelineItemPublisher;

  @Autowired
  private OrderFormDraftService orderFormDraftService;

  @Autowired
  private OrderFormDraftConsumeService orderFormDraftConsumeService;

  @Autowired
  private OrderConfirmationQueryService orderConfirmationQueryService;

  @Autowired
  private OrderConfirmationStateService orderConfirmationStateService;

  @Autowired
  private OrderQueryService orderQueryService;

  @Autowired
  private NotificationService notificationService;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private OrderJpaRepository orderJpaRepository;

  @Autowired
  private AssetJpaRepository assetJpaRepository;

  @Autowired
  private AssetVariantJpaRepository assetVariantJpaRepository;

  @Autowired
  private GalleryItemJpaRepository galleryItemJpaRepository;

  @Autowired
  private PaymentAttemptJpaRepository paymentAttemptJpaRepository;

  @Autowired
  private ChatTimelineItemJpaRepository chatTimelineItemJpaRepository;

  @Autowired
  private InquiryJpaRepository inquiryJpaRepository;

  @Autowired
  private NotificationJpaRepository notificationJpaRepository;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private InMemoryOrderFormDraftStorePort draftStorePort;

  @BeforeEach
  void setUp() {
    draftStorePort.clear();
  }

  @Test
  @DisplayName("문의 목록, 상세, 타임라인은 실제 저장된 채팅 이벤트 기준으로 조회된다")
  void queriesInquiryListDetailAndTimeline() {
    Fixture fixture = prepareFixture("workflow-list");
    chatTimelineItemPublisher.publishOrderFormSubmission(
        fixture.inquiry().getId(), fixture.buyer().getId(), fixture.submission().getId());
    SendOrderConfirmationResult confirmation = sendConfirmation(fixture, "주문확인서");

    List<InquiryListItem> buyerItems =
        inquiryListService.getBuyerInquiries(fixture.buyer().getId(), null);
    List<InquiryListItem> sellerItems = inquiryListService.getSellerInquiries(
        fixture.store().id(), fixture.seller().getId(), null);
    InquiryChatDetail buyerDetail = inquiryChatDetailQueryService.getBuyerDetail(fixture.inquiry());
    ChatTimelinePage timelinePage = chatTimelineQueryService.execute(
        fixture.inquiry().getId(), new ChatTimelineQuery(null, null, 10));
    ChatTimelineItemResult confirmationEvent =
        findTimelineItem(timelinePage, ChatTimelineItemType.ORDER_CONFIRMATION);
    ChatTimelineItemResult submissionEvent =
        findTimelineItem(timelinePage, ChatTimelineItemType.ORDER_FORM_SUBMISSION);
    ChatTimelineItem persistedConfirmationEvent =
        chatTimelineItemJpaRepository.findById(confirmationEvent.eventId()).orElseThrow();
    ChatTimelineItem persistedSubmissionEvent =
        chatTimelineItemJpaRepository.findById(submissionEvent.eventId()).orElseThrow();

    assertEquals(1, buyerItems.size());
    assertEquals(1, buyerItems.get(0).unreadCount());
    assertEquals(InquiryStatus.WAITING, buyerItems.get(0).status());
    assertEquals(1, sellerItems.size());
    assertEquals(1, sellerItems.get(0).unreadCount());
    assertEquals(fixture.seller().getName(), buyerDetail.participant().name());
    assertFalse(timelinePage.hasNext());
    assertEquals(2, timelinePage.items().size());
    assertEquals(
        confirmation.orderConfirmation().id(), persistedConfirmationEvent.getReferenceId());
    assertEquals(fixture.submission().getId(), persistedSubmissionEvent.getReferenceId());
    assertEquals(confirmation.orderConfirmation().id(), confirmationEvent.referenceId());
    assertEquals(fixture.submission().getId(), submissionEvent.referenceId());
    assertEquals(
        confirmation.orderConfirmation().id(),
        ChatTimelineItemResponse.from(confirmationEvent).referenceId());
    assertEquals(
        fixture.submission().getId(),
        ChatTimelineItemResponse.from(submissionEvent).referenceId());

    inquiryListService.markBuyerRead(fixture.inquiry().getId(), fixture.buyer().getId());
    assertEquals(
        0,
        inquiryListService
            .getBuyerInquiries(fixture.buyer().getId(), InquiryStatus.WAITING)
            .get(0)
            .unreadCount());
  }

  @Test
  @DisplayName("주문서 draft는 검증 후 저장되고 로그인 후 실제 문의 제출로 소비된다")
  void createsAndConsumesOrderFormDraft() throws Exception {
    Fixture fixture = prepareFixtureWithoutInquiry("workflow-draft");
    UUID galleryAssetId =
        createVisibleGalleryAsset(fixture.store().id(), fixture.seller().getId());
    CreateOrderFormDraftCommand draftCommand = new CreateOrderFormDraftCommand(
        fixture.store().id(),
        fixture.form().id(),
        LocalDate.parse("2030-08-30"),
        LocalTime.parse("15:00"),
        true,
        List.of(
            new CreateOrderFormDraftCommand.FormAnswer(
                fixture.form().optionGroups().get(0).id(),
                selections(selection("menu").put("text", "바닐라 케이크"))),
            new CreateOrderFormDraftCommand.FormAnswer(
                fixture.form().optionGroups().get(1).id(), selections(selection("size-10")))),
        new CreateOrderFormDraftCommand.ReferenceAsset(
            galleryAssetId, OrderFormReferenceAssetSource.STORE_GALLERY),
        true);

    OrderFormDraftResult draft = orderFormDraftService.create(draftCommand);
    OrderFormDraftData storedDraft =
        draftStorePort.findByDraftKey(draft.draftKey()).orElseThrow();
    OrderFormDraftConsumeResult consumed = orderFormDraftConsumeService.consume(
        new ConsumeOrderFormDraftCommand(draft.draftKey(), fixture.buyer().getId()));
    ChatTimelinePage timelinePage = chatTimelineQueryService.execute(
        consumed.inquiry().getId(), new ChatTimelineQuery(null, null, 10));
    ChatTimelineItemResult submissionEvent =
        findTimelineItem(timelinePage, ChatTimelineItemType.ORDER_FORM_SUBMISSION);
    ChatTimelineItem persistedSubmissionEvent =
        chatTimelineItemJpaRepository.findById(submissionEvent.eventId()).orElseThrow();

    assertFalse(draft.draftKey().isBlank());
    assertEquals(fixture.store().id(), storedDraft.storeId());
    assertEquals(
        "바닐라 케이크", storedDraft.formAnswers().get(0).value().get(0).get("text").asText());
    assertEquals(galleryAssetId, storedDraft.startReferenceAsset().assetId());
    assertFalse(draftStorePort.findByDraftKey(draft.draftKey()).isPresent());
    assertEquals(InquiryStatus.WAITING, consumed.inquiry().getStatus());
    assertEquals(fixture.buyer().getId(), consumed.submission().getSubmittedBy());
    assertEquals(fixture.form().id(), consumed.submission().getTemplateId());
    JsonNode submissionReferenceAssets =
        objectMapper.readTree(consumed.submission().getReferenceAssets());
    assertEquals(1, submissionReferenceAssets.size());
    assertEquals(
        galleryAssetId.toString(),
        submissionReferenceAssets.get(0).get("assetId").asText());
    assertEquals("STORE_GALLERY", submissionReferenceAssets.get(0).get("source").asText());
    assertEquals(0, submissionReferenceAssets.get(0).get("sortOrder").asInt());
    assertEquals(consumed.submission().getId(), persistedSubmissionEvent.getReferenceId());

    InquiryChatDetail chatDetail = inquiryChatDetailQueryService.getBuyerDetail(consumed.inquiry());
    assertEquals(galleryAssetId, chatDetail.startReferenceAsset().assetId());
    assertEquals(
        "https://assets.example.test/processed/" + galleryAssetId + "/start-reference_640.webp",
        chatDetail.startReferenceAsset().deliveryUrl());
  }

  @Test
  @DisplayName("주문서 수정 draft는 기존 주문 시작 참조 이미지를 유지한다")
  void consumesDraftWithoutStartReferenceAssetsAsUpdate() {
    Fixture fixture = prepareFixtureWithoutInquiry("workflow-draft-update");
    UUID galleryAssetId =
        createVisibleGalleryAsset(fixture.store().id(), fixture.seller().getId());
    OrderFormDraftResult firstDraft = createDraft(
        fixture,
        "바닐라 케이크",
        new CreateOrderFormDraftCommand.ReferenceAsset(
            galleryAssetId, OrderFormReferenceAssetSource.STORE_GALLERY),
        true);
    OrderFormDraftConsumeResult firstConsumed = orderFormDraftConsumeService.consume(
        new ConsumeOrderFormDraftCommand(firstDraft.draftKey(), fixture.buyer().getId()));

    OrderFormDraftResult updateDraft = createDraft(fixture, "초코 케이크", null, false);
    OrderFormDraftConsumeResult updated = orderFormDraftConsumeService.consume(
        new ConsumeOrderFormDraftCommand(updateDraft.draftKey(), fixture.buyer().getId()));
    InquiryChatDetail chatDetail = inquiryChatDetailQueryService.getBuyerDetail(updated.inquiry());
    ChatTimelinePage timelinePage = chatTimelineQueryService.execute(
        updated.inquiry().getId(), new ChatTimelineQuery(null, null, 10));

    assertEquals(firstConsumed.inquiry().getId(), updated.inquiry().getId());
    assertEquals(galleryAssetId, chatDetail.startReferenceAsset().assetId());
    assertEquals(
        2,
        timelinePage.items().stream()
            .filter(item -> item.type() == ChatTimelineItemType.ORDER_FORM_SUBMISSION)
            .count());
    assertEquals(
        1,
        notificationJpaRepository
            .findAllByUserIdOrderByCreatedAtDesc(fixture.seller().getId())
            .stream()
            .filter(notification -> notification.getType() == NotificationType.ORDER_FORM_UPDATED)
            .count());
  }

  @Test
  @DisplayName("판매자 휴지통 상담은 첫 주문서 제출 시 접수대기로 자동 복구된다")
  void consumesDraftAndReopensSellerTrashInquiry() {
    Fixture fixture = prepareFixtureWithoutInquiry("workflow-draft-trash-reopen");
    UUID galleryAssetId =
        createVisibleGalleryAsset(fixture.store().id(), fixture.seller().getId());
    Inquiry inquiry = inquiryOpenService.open(
        OpenInquiryCommand.of(fixture.store().id(), fixture.buyer().getId()));
    inquiry.markInProgressOnSellerReview();
    inquiryListService.moveSellerToTrash(inquiry.getId(), fixture.store().id());

    OrderFormDraftResult draft = createDraft(
        fixture,
        "바닐라 케이크",
        new CreateOrderFormDraftCommand.ReferenceAsset(
            galleryAssetId, OrderFormReferenceAssetSource.STORE_GALLERY),
        true);
    OrderFormDraftConsumeResult consumed = orderFormDraftConsumeService.consume(
        new ConsumeOrderFormDraftCommand(draft.draftKey(), fixture.buyer().getId()));

    assertSellerWaitingListTarget(consumed.inquiry(), fixture);
  }

  @Test
  @DisplayName("자동 비우기된 판매자 휴지통 상담도 주문서 수정 재제출 시 접수대기로 자동 복구된다")
  void consumesUpdateDraftAndReopensSellerPurgedInquiry() {
    Fixture fixture = prepareFixtureWithoutInquiry("workflow-draft-purge-reopen");
    OrderFormDraftResult firstDraft = createDraft(fixture, "바닐라 케이크", null, false);
    OrderFormDraftConsumeResult firstConsumed = orderFormDraftConsumeService.consume(
        new ConsumeOrderFormDraftCommand(firstDraft.draftKey(), fixture.buyer().getId()));
    inquiryListService.moveSellerToTrash(
        firstConsumed.inquiry().getId(), fixture.store().id());
    ReflectionTestUtils.setField(
        firstConsumed.inquiry(), "sellerPurgedAt", Instant.parse("2026-09-25T00:00:00Z"));
    inquiryJpaRepository.saveAndFlush(firstConsumed.inquiry());

    OrderFormDraftResult updateDraft = createDraft(fixture, "초코 케이크", null, false);
    OrderFormDraftConsumeResult updated = orderFormDraftConsumeService.consume(
        new ConsumeOrderFormDraftCommand(updateDraft.draftKey(), fixture.buyer().getId()));

    assertEquals(firstConsumed.inquiry().getId(), updated.inquiry().getId());
    assertSellerWaitingListTarget(updated.inquiry(), fixture);
  }

  @Test
  @DisplayName("주문서 draft는 픽업 가능 시간 외 요청을 저장하지 않는다")
  void rejectsDraftWithUnavailablePickupTime() {
    Fixture fixture = prepareFixtureWithoutInquiry("workflow-draft-pickup");

    BaseException exception = assertThrows(
        BaseException.class,
        () -> orderFormDraftService.create(new CreateOrderFormDraftCommand(
            fixture.store().id(),
            fixture.form().id(),
            LocalDate.parse("2030-08-30"),
            LocalTime.parse("09:30"),
            true,
            List.of(
                new CreateOrderFormDraftCommand.FormAnswer(
                    fixture.form().optionGroups().get(0).id(),
                    selections(selection("menu").put("text", "바닐라 케이크"))),
                new CreateOrderFormDraftCommand.FormAnswer(
                    fixture.form().optionGroups().get(1).id(), selections(selection("size-10")))),
            null,
            false)));

    assertEquals(OrderFormErrorCode.ORDER_FORM_PICKUP_UNAVAILABLE, exception.getErrorCode());
  }

  @Test
  @DisplayName("주문확인 조회와 상태 변경은 구매자/판매자 권한과 현재 상태를 함께 검증한다")
  void queriesAndTransitionsOrderConfirmations() {
    Fixture fixture = prepareFixture("workflow-confirmation");
    SendOrderConfirmationResult first = sendConfirmation(fixture, "1차 확인서");
    OrderConfirmation viewed = orderConfirmationStateService.markBuyerViewed(
        fixture.inquiry().getId(),
        first.orderConfirmation().id(),
        fixture.buyer().getId());
    OrderConfirmation revisionRequested = orderConfirmationStateService.requestRevision(
        fixture.inquiry().getId(),
        first.orderConfirmation().id(),
        fixture.buyer().getId());
    assertEquals(OrderConfirmationStatus.REVISION_REQUESTED, revisionRequested.getStatus());

    ChatTimelinePage timelinePage = chatTimelineQueryService.execute(
        fixture.inquiry().getId(), new ChatTimelineQuery(null, null, 10));
    ChatTimelineItemResult revisionEvent =
        findTimelineItem(timelinePage, ChatTimelineItemType.ORDER_CONFIRMATION_REVISION);
    assertEquals(first.orderConfirmation().id(), revisionEvent.referenceId());
    assertEquals(
        first.orderConfirmation().id(),
        ChatTimelineItemResponse.from(revisionEvent).referenceId());

    SendOrderConfirmationResult replacement = sendConfirmation(fixture, "수정 확인서");

    OrderConfirmation replaced = orderConfirmationStateService.replace(
        fixture.inquiry().getId(),
        first.orderConfirmation().id(),
        replacement.orderConfirmation().id(),
        fixture.store().id());

    assertNotNull(viewed.getBuyerViewedAt());
    assertEquals(OrderConfirmationStatus.REPLACED, replaced.getStatus());
    assertEquals(replacement.orderConfirmation().id(), replaced.getReplacedByConfirmationId());
    assertEquals(
        2,
        orderConfirmationQueryService
            .getSellerHistory(fixture.inquiry().getId(), fixture.store().id())
            .size());
    assertEquals(
        replacement.orderConfirmation().id(),
        orderConfirmationQueryService
            .getBuyerConfirmation(
                fixture.inquiry().getId(),
                replacement.orderConfirmation().id(),
                fixture.buyer().getId())
            .getId());
  }

  @Test
  @DisplayName("주문 조회는 구매자와 스토어 소유 경계를 기준으로 결과를 제한한다")
  void queriesOrdersByBuyerAndSellerBoundary() {
    Fixture fixture = prepareFixture("workflow-order");
    SendOrderConfirmationResult confirmation = sendConfirmation(fixture, "결제 완료 확인서");
    PaymentAttempt paymentAttempt = paymentAttemptJpaRepository.saveAndFlush(PaymentAttempt.create(
        confirmation.orderConfirmation().id(),
        fixture.buyer().getId(),
        "point3-" + UUID.randomUUID(),
        null,
        41000,
        Instant.parse("2026-08-25T01:00:00Z")));
    Order order = orderJpaRepository.saveAndFlush(Order.create(
        fixture.store().id(),
        fixture.buyer().getId(),
        fixture.inquiry().getId(),
        confirmation.orderConfirmation().id(),
        paymentAttempt.getId(),
        "P3-20260825-0001",
        "초코 케이크",
        "딸기 토핑",
        41000,
        Instant.parse("2026-09-02T04:00:00Z")));

    assertEquals(
        order.getId(),
        orderQueryService.getBuyerOrders(fixture.buyer().getId()).get(0).id());
    assertEquals(
        order.getId(),
        orderQueryService
            .getBuyerOrder(order.getId(), fixture.buyer().getId())
            .order()
            .id());
    assertEquals(
        order.getId(),
        orderQueryService.getSellerOrders(fixture.store().id()).get(0).id());
    assertEquals(
        order.getId(),
        orderQueryService
            .getSellerOrder(order.getId(), fixture.store().id())
            .order()
            .id());

    User otherBuyer = saveUser(UserRole.BUYER, "workflow-order-other");
    BaseException buyerException = assertThrows(
        BaseException.class,
        () -> orderQueryService.getBuyerOrder(order.getId(), otherBuyer.getId()));
    assertEquals(OrderErrorCode.ORDER_NOT_FOUND, buyerException.getErrorCode());

    StoreResult otherStore =
        prepareFixtureWithoutInquiry("workflow-order-other-store").store();
    BaseException sellerException = assertThrows(
        BaseException.class,
        () -> orderQueryService.getSellerOrder(order.getId(), otherStore.id()));
    assertEquals(OrderErrorCode.ORDER_NOT_FOUND, sellerException.getErrorCode());
  }

  @Test
  @DisplayName("알림 조회와 읽음 처리는 사용자 소유 알림만 대상으로 한다")
  void queriesAndReadsNotificationsByOwner() {
    User user = saveUser(UserRole.BUYER, "workflow-notification");
    User otherUser = saveUser(UserRole.BUYER, "workflow-notification-other");
    Notification notification = notificationJpaRepository.saveAndFlush(Notification.create(
        user.getId(),
        NotificationType.ORDER_STATUS_CHANGED,
        NotificationReferenceType.INQUIRY,
        UUID.randomUUID(),
        "주문확인서가 도착했습니다",
        "확인 후 결제를 진행해 주세요."));

    NotificationResult listItem =
        notificationService.getNotifications(user.getId()).get(0);
    assertEquals(notification.getId(), listItem.id());
    assertEquals(NotificationType.ORDER_STATUS_CHANGED, listItem.type());
    assertEquals(NotificationReferenceType.INQUIRY, listItem.referenceType());
    assertEquals("주문확인서가 도착했습니다", listItem.title());
    assertEquals(1, notificationService.getUnreadCount(user.getId()));
    assertEquals(
        notification.getId(),
        notificationService.getNotification(notification.getId(), user.getId()).id());
    assertNotNull(notificationService.read(notification.getId(), user.getId()).readAt());
    assertEquals(0, notificationService.getUnreadCount(user.getId()));

    BaseException exception = assertThrows(
        BaseException.class,
        () -> notificationService.getNotification(notification.getId(), otherUser.getId()));
    assertEquals(NotificationErrorCode.NOTIFICATION_NOT_FOUND, exception.getErrorCode());
  }

  private ChatTimelineItemResult findTimelineItem(
      ChatTimelinePage timelinePage, ChatTimelineItemType type) {
    return timelinePage.items().stream()
        .filter(item -> item.type() == type)
        .findFirst()
        .orElseThrow();
  }

  private void assertSellerWaitingListTarget(Inquiry inquiry, Fixture fixture) {
    assertNull(inquiry.getSellerDeletedAt());
    assertNull(inquiry.getSellerPurgedAt());
    assertEquals(InquiryStatus.WAITING, inquiry.getStatus());
    assertEquals(InquiryStatus.WAITING, inquiry.statusForSeller());
    assertTrue(inquiry.isSellerVisible());
    assertFalse(inquiry.isSellerTrashed());
    assertTrue(inquiryListService
        .getSellerInquiries(fixture.store().id(), fixture.seller().getId(), InquiryStatus.WAITING)
        .stream()
        .anyMatch(item -> item.inquiryId().equals(inquiry.getId())));
    assertFalse(inquiryListService
        .getSellerInquiries(fixture.store().id(), fixture.seller().getId(), InquiryStatus.TRASH)
        .stream()
        .anyMatch(item -> item.inquiryId().equals(inquiry.getId())));
  }

  private Fixture prepareFixture(String prefix) {
    Fixture fixture = prepareFixtureWithoutInquiry(prefix);
    Inquiry inquiry = inquiryOpenService.open(
        OpenInquiryCommand.of(fixture.store().id(), fixture.buyer().getId()));
    OrderFormSubmission submission =
        submitOrderForm(fixture.store().id(), fixture.buyer().getId(), inquiry, fixture.form());
    return new Fixture(
        fixture.seller(), fixture.buyer(), fixture.store(), fixture.form(), inquiry, submission);
  }

  private Fixture prepareFixtureWithoutInquiry(String prefix) {
    User seller = saveUser(UserRole.SELLER, prefix + "-seller");
    User buyer = saveUser(UserRole.BUYER, prefix + "-buyer");
    StoreResult store = storeService.create(new CreateStoreCommand(
        seller.getId(),
        "P3 베이커리 " + prefix,
        null,
        "주문제작 케이크 스토어",
        "010-1234-5678",
        true,
        null,
        "{\"mon\":\"10:00-18:00\"}",
        "{\"leadTimeDays\":3}",
        "서울특별시 중구"));
    OrderFormResult form = orderFormService.create(new CreateOrderFormCommand(
        store.id(),
        "주문서 " + prefix,
        List.of(
            optionGroup("메뉴명", true, 0, option("메뉴명", "menu", OptionInputType.TEXT, 0L, 0)),
            optionGroup(
                "사이즈", true, 1, option("10호", "size-10", OptionInputType.SELECT, 38000L, 0)))));
    savePickupSettings(store.id());

    return new Fixture(seller, buyer, store, form, null, null);
  }

  private OrderFormDraftResult createDraft(
      Fixture fixture,
      String menuName,
      CreateOrderFormDraftCommand.ReferenceAsset startReferenceAsset,
      boolean startReferenceAssetProvided) {
    return orderFormDraftService.create(new CreateOrderFormDraftCommand(
        fixture.store().id(),
        fixture.form().id(),
        LocalDate.parse("2030-08-30"),
        LocalTime.parse("15:00"),
        true,
        draftAnswers(fixture.form(), menuName),
        startReferenceAsset,
        startReferenceAssetProvided));
  }

  private List<CreateOrderFormDraftCommand.FormAnswer> draftAnswers(
      OrderFormResult form, String menuName) {
    return List.of(
        new CreateOrderFormDraftCommand.FormAnswer(
            form.optionGroups().get(0).id(), selections(selection("menu").put("text", menuName))),
        new CreateOrderFormDraftCommand.FormAnswer(
            form.optionGroups().get(1).id(), selections(selection("size-10"))));
  }

  private void savePickupSettings(UUID storeId) {
    storeSettingService.update(new UpdateStoreSettingCommand(
        storeId,
        0,
        0,
        java.util.Arrays.stream(DayOfWeek.values())
            .map(day -> new UpdateStoreSettingCommand.WeeklyPickupSetting(
                day, LocalTime.of(10, 0), LocalTime.of(18, 0), true))
            .toList(),
        List.of()));
  }

  private OrderFormSubmission submitOrderForm(
      UUID storeId, UUID buyerUserId, Inquiry inquiry, OrderFormResult form) {
    return submissionService.create(new CreateOrderFormSubmissionCommand(
        storeId,
        buyerUserId,
        inquiry.getId(),
        form.id(),
        List.of(
            new CreateOrderFormSubmissionCommand.FormAnswer(
                form.optionGroups().get(0).id(),
                selections(selection("menu").put("text", "초코 케이크"))),
            new CreateOrderFormSubmissionCommand.FormAnswer(
                form.optionGroups().get(1).id(), selections(selection("size-10")))),
        new CreateOrderFormSubmissionCommand.PickupRequest(
            LocalDate.parse("2030-08-30"), LocalTime.parse("13:30")),
        new CreateOrderFormSubmissionCommand.NoticeAgreement(true),
        CreateOrderFormSubmissionCommand.emptyReferenceAssets()));
  }

  private SendOrderConfirmationResult sendConfirmation(Fixture fixture, String title) {
    fixture.submission().markSellerViewed(Instant.now());
    return orderConfirmationService.send(new SendOrderConfirmationCommand(
        fixture.inquiry().getId(),
        fixture.store().id(),
        fixture.seller().getId(),
        fixture.submission().getId(),
        title,
        "초코 시트, 딸기 토핑",
        41000,
        Instant.parse("2030-08-30T04:30:00Z"),
        List.of(new SendOrderConfirmationCommand.AdditionalItem("토핑", "딸기", 3000L)),
        "픽업 10분 전에 연락 주세요."));
  }

  private User saveUser(UserRole role, String prefix) {
    return userJpaRepository.saveAndFlush(User.create(
        UUID.randomUUID().toString(),
        uniqueEmail(prefix),
        prefix,
        role,
        "010-0000-0000",
        SignupProvider.GOOGLE));
  }

  private UUID createVisibleGalleryAsset(UUID storeId, UUID sellerId) {
    Asset asset = assetJpaRepository.saveAndFlush(Asset.create(
        UUID.randomUUID(),
        sellerId,
        "start-reference.png",
        "image/png",
        1024,
        "original/" + UUID.randomUUID() + "/start-reference.png"));
    assetVariantJpaRepository.saveAndFlush(AssetVariant.create(
        asset,
        AssetVariantType.MEDIUM,
        "processed/" + asset.getId() + "/start-reference_640.webp",
        "image/webp",
        640,
        640,
        512));
    StoreGalleryItem galleryItem = StoreGalleryItem.create(storeId, asset.getId(), 0);
    galleryItem.show();
    galleryItemJpaRepository.saveAndFlush(galleryItem);
    return asset.getId();
  }

  private CreateOrderFormCommand.OptionGroup optionGroup(
      String label, boolean required, int sortOrder, OrderFormOptionCommand option) {
    return new CreateOrderFormCommand.OptionGroup(
        label,
        SelectionType.SINGLE,
        required,
        sortOrder,
        OrderFormCategory.SIZE,
        OrderFormCategory.SIZE.getTitle(),
        null,
        0,
        List.of(option));
  }

  private OrderFormOptionCommand option(
      String label, String value, OptionInputType inputType, Long price, int sortOrder) {
    return new OrderFormOptionCommand(label, value, inputType, price, null, null, true, sortOrder);
  }

  private com.fasterxml.jackson.databind.node.ObjectNode selection(String optionValue) {
    return objectMapper.getNodeFactory().objectNode().put("optionValue", optionValue);
  }

  private com.fasterxml.jackson.databind.node.ArrayNode selections(JsonNode... selectedOptions) {
    com.fasterxml.jackson.databind.node.ArrayNode selections =
        objectMapper.getNodeFactory().arrayNode();
    for (JsonNode selectedOption : selectedOptions) {
      selections.add(selectedOption);
    }
    return selections;
  }

  private record Fixture(
      User seller,
      User buyer,
      StoreResult store,
      OrderFormResult form,
      Inquiry inquiry,
      OrderFormSubmission submission) {}

  @TestConfiguration
  static class DraftStoreTestConfiguration {

    @Bean
    @Primary
    InMemoryOrderFormDraftStorePort inMemoryOrderFormDraftStorePort() {
      return new InMemoryOrderFormDraftStorePort();
    }
  }

  static class InMemoryOrderFormDraftStorePort implements OrderFormDraftStorePort {

    private final Map<String, OrderFormDraftData> drafts = new HashMap<>();

    @Override
    public OrderFormDraftResult save(OrderFormDraftData draftData) {
      String draftKey = UUID.randomUUID().toString();
      drafts.put(draftKey, draftData);
      return new OrderFormDraftResult(draftKey, Instant.parse("2026-08-25T03:00:00Z"));
    }

    @Override
    public Optional<OrderFormDraftData> findByDraftKey(String draftKey) {
      return Optional.ofNullable(drafts.get(draftKey));
    }

    @Override
    public void delete(String draftKey) {
      drafts.remove(draftKey);
    }

    void clear() {
      drafts.clear();
    }
  }
}
