package io.point3.p3api.inquiry.application.list;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.point3.p3api.IntegrationTestSupport;
import io.point3.p3api.asset.domain.entity.Asset;
import io.point3.p3api.asset.infrastructure.persistence.AssetJpaRepository;
import io.point3.p3api.assetvariant.domain.entity.AssetVariant;
import io.point3.p3api.assetvariant.domain.type.AssetVariantType;
import io.point3.p3api.assetvariant.infrastructure.persistence.AssetVariantJpaRepository;
import io.point3.p3api.chat.application.timeline.ChatTimelineItemPublisher;
import io.point3.p3api.chat.domain.entity.ChatMessage;
import io.point3.p3api.chat.domain.type.ChatTimelineItemType;
import io.point3.p3api.chat.infrastructure.persistence.ChatMessageJpaRepository;
import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.ChatErrorCode;
import io.point3.p3api.inquiry.application.chat.InquiryChatAccessService;
import io.point3.p3api.inquiry.application.command.OpenInquiryCommand;
import io.point3.p3api.inquiry.application.open.InquiryOpenService;
import io.point3.p3api.inquiry.application.result.InquiryListItem;
import io.point3.p3api.inquiry.domain.entity.Inquiry;
import io.point3.p3api.inquiry.domain.entity.OrderFormSubmission;
import io.point3.p3api.inquiry.domain.type.InquiryStatus;
import io.point3.p3api.inquiry.infrastructure.persistence.InquiryJpaRepository;
import io.point3.p3api.inquiry.infrastructure.persistence.OrderFormSubmissionJpaRepository;
import io.point3.p3api.orderform.domain.entity.OrderFormTemplate;
import io.point3.p3api.orderform.infrastructure.persistence.OrderFormTemplateJpaRepository;
import io.point3.p3api.store.application.StoreService;
import io.point3.p3api.store.application.create.CreateStoreCommand;
import io.point3.p3api.store.application.result.StoreResult;
import io.point3.p3api.user.domain.entity.User;
import io.point3.p3api.user.domain.type.SignupProvider;
import io.point3.p3api.user.domain.type.UserRole;
import io.point3.p3api.user.infrastructure.persistence.UserJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "p3.asset.delivery.base-url=https://assets.example.test")
class InquiryListServiceIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private InquiryListService inquiryListService;

  @Autowired
  private InquiryChatAccessService inquiryChatAccessService;

  @Autowired
  private InquiryOpenService inquiryOpenService;

  @Autowired
  private ChatTimelineItemPublisher chatTimelineItemPublisher;

  @Autowired
  private ChatMessageJpaRepository chatMessageJpaRepository;

  @Autowired
  private OrderFormSubmissionJpaRepository orderFormSubmissionJpaRepository;

  @Autowired
  private OrderFormTemplateJpaRepository orderFormTemplateJpaRepository;

  @Autowired
  private StoreService storeService;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private AssetJpaRepository assetJpaRepository;

  @Autowired
  private AssetVariantJpaRepository assetVariantJpaRepository;

  @Autowired
  private InquiryJpaRepository inquiryJpaRepository;

  @Test
  @DisplayName("상담 목록은 읽지 않음과 사용자별 휴지통 상태를 기준으로 필터링한다")
  void filtersUnreadAndParticipantTrash() {
    Fixture fixture = prepareFixture("inquiry-list");
    chatTimelineItemPublisher.publishMessage(
        fixture.firstInquiry().getId(), fixture.firstSeller().getId(), UUID.randomUUID());

    List<InquiryListItem> unreadItems =
        inquiryListService.getBuyerInquiries(fixture.buyer().getId(), null, true);
    inquiryListService.moveBuyerToTrash(
        fixture.firstInquiry().getId(), fixture.buyer().getId());
    List<InquiryListItem> activeBuyerItems =
        inquiryListService.getBuyerInquiries(fixture.buyer().getId(), null);
    List<InquiryListItem> buyerTrashItems =
        inquiryListService.getBuyerInquiries(fixture.buyer().getId(), InquiryStatus.TRASH);
    List<InquiryListItem> sellerItems = inquiryListService.getSellerInquiries(
        fixture.firstStore().id(), fixture.firstSeller().getId(), null);

    assertEquals(1, unreadItems.size());
    assertEquals(fixture.firstInquiry().getId(), unreadItems.get(0).inquiryId());
    assertEquals(1, activeBuyerItems.size());
    assertEquals(fixture.secondInquiry().getId(), activeBuyerItems.get(0).inquiryId());
    assertEquals(1, buyerTrashItems.size());
    assertEquals(InquiryStatus.TRASH, buyerTrashItems.get(0).status());
    assertEquals(1, sellerItems.size());
    assertEquals(fixture.firstInquiry().getId(), sellerItems.get(0).inquiryId());

    inquiryListService.restoreBuyerFromTrash(
        fixture.firstInquiry().getId(), fixture.buyer().getId());

    assertEquals(
        2, inquiryListService.getBuyerInquiries(fixture.buyer().getId(), null).size());
  }

  @Test
  @DisplayName("판매자 상담 목록은 최신 이벤트와 최근 주문서 제출 요약을 응답한다")
  void getsSellerInquiriesWithSummaries() {
    Fixture fixture = prepareFixture("inquiry-list-summary");
    OrderFormTemplate template = orderFormTemplateJpaRepository.saveAndFlush(
        OrderFormTemplate.create(fixture.firstStore().id(), "기본 주문서"));
    ChatMessage message = chatMessageJpaRepository.saveAndFlush(
        ChatMessage.create(fixture.firstInquiry().getId(), fixture.buyer().getId(), "안녕하세요"));
    chatTimelineItemPublisher.publishMessage(
        fixture.firstInquiry().getId(), fixture.buyer().getId(), message.getId());
    OrderFormSubmission submission =
        orderFormSubmissionJpaRepository.saveAndFlush(OrderFormSubmission.create(
            fixture.firstInquiry().getId(),
            template.getId(),
            fixture.buyer().getId(),
            LocalDate.parse("2030-08-30"),
            LocalTime.parse("13:30"),
            "[]",
            "[]",
            true));

    List<InquiryListItem> sellerItems = inquiryListService.getSellerInquiries(
        fixture.firstStore().id(), fixture.firstSeller().getId(), null);

    InquiryListItem item = sellerItems.getFirst();
    assertEquals(fixture.firstInquiry().getId(), item.inquiryId());
    assertNotNull(item.latestEvent().eventId());
    assertEquals(message.getId(), item.latestEvent().referenceId());
    assertEquals(ChatTimelineItemType.MESSAGE, item.latestEvent().type());
    assertEquals(fixture.buyer().getId(), item.latestEvent().senderUserId());
    assertEquals("안녕하세요", item.latestEvent().content());
    assertEquals(item.latestEvent().createdAt(), item.latestEventAt());
    assertEquals(submission.getId(), item.latestOrderFormSubmission().submissionId());
    assertEquals(submission.getSubmittedAt(), item.latestOrderFormSubmission().submittedAt());
  }

  @Test
  @DisplayName("판매자 상담 목록은 문의별 읽지 않은 항목 수를 일괄 조회한다")
  void getsSellerUnreadCountsForMultipleInquiries() {
    Fixture fixture = prepareFixture("inquiry-list-unread-counts");
    User otherBuyer = saveUser(UserRole.BUYER, "inquiry-list-unread-counts-other-buyer");
    Inquiry otherInquiry = inquiryOpenService.open(
        OpenInquiryCommand.of(fixture.firstStore().id(), otherBuyer.getId()));

    chatTimelineItemPublisher.publishMessage(
        fixture.firstInquiry().getId(), fixture.buyer().getId(), UUID.randomUUID());
    chatTimelineItemPublisher.publishMessage(
        fixture.firstInquiry().getId(), fixture.buyer().getId(), UUID.randomUUID());
    chatTimelineItemPublisher.publishMessage(
        fixture.firstInquiry().getId(), fixture.firstSeller().getId(), UUID.randomUUID());
    chatTimelineItemPublisher.publishMessage(
        otherInquiry.getId(), otherBuyer.getId(), UUID.randomUUID());
    otherInquiry.markSellerRead(Instant.now().plusSeconds(3600));
    inquiryJpaRepository.saveAndFlush(otherInquiry);

    List<InquiryListItem> sellerItems = inquiryListService.getSellerInquiries(
        fixture.firstStore().id(), fixture.firstSeller().getId(), null);
    Map<UUID, Long> unreadCountsByInquiryId = sellerItems.stream()
        .collect(Collectors.toMap(InquiryListItem::inquiryId, InquiryListItem::unreadCount));
    List<InquiryListItem> unreadOnlyItems = inquiryListService.getSellerInquiries(
        fixture.firstStore().id(), fixture.firstSeller().getId(), null, true);

    assertEquals(2, sellerItems.size());
    assertEquals(2L, unreadCountsByInquiryId.get(fixture.firstInquiry().getId()));
    assertEquals(0L, unreadCountsByInquiryId.get(otherInquiry.getId()));
    assertEquals(1, unreadOnlyItems.size());
    assertEquals(fixture.firstInquiry().getId(), unreadOnlyItems.getFirst().inquiryId());
  }

  @Test
  @DisplayName("판매자 상담 목록은 구매자 프로필 이미지 delivery URL을 응답한다")
  void getsSellerInquiriesWithBuyerProfileImage() {
    Fixture fixture = prepareFixture("inquiry-list-profile");
    Asset profileAsset = saveAsset(fixture.buyer().getId(), "original/buyer-profile.png");
    saveVariant(profileAsset, "processed/buyer-profile_640.webp");
    fixture.buyer().updateProfileAsset(profileAsset.getId());
    userJpaRepository.saveAndFlush(fixture.buyer());

    List<InquiryListItem> sellerItems = inquiryListService.getSellerInquiries(
        fixture.firstStore().id(), fixture.firstSeller().getId(), null);

    InquiryListItem item = sellerItems.getFirst();
    assertEquals(fixture.buyer().getId(), item.detail().participant().userId());
    assertEquals(
        "https://assets.example.test/processed/buyer-profile_640.webp",
        item.detail().participant().profileImageDeliveryUrl());
  }

  @Test
  @DisplayName("휴지통 이동 1개월이 지난 상담은 해당 사용자 목록에서 비운다")
  void purgesExpiredTrashForParticipant() {
    Fixture fixture = prepareFixture("inquiry-trash-purge");
    fixture.firstInquiry().moveBuyerToTrash(Instant.parse("2026-07-20T00:00:00Z"));
    inquiryJpaRepository.saveAndFlush(fixture.firstInquiry());

    inquiryListService.purgeExpiredTrash();
    BaseException exception = assertThrows(
        BaseException.class,
        () -> inquiryChatAccessService.getBuyerInquiry(
            fixture.firstInquiry().getId(), fixture.buyer().getId()));

    assertEquals(ChatErrorCode.CHAT_INQUIRY_NOT_FOUND, exception.getErrorCode());
    assertEquals(
        0,
        inquiryListService
            .getBuyerInquiries(fixture.buyer().getId(), InquiryStatus.TRASH)
            .size());
    assertEquals(
        1,
        inquiryListService
            .getSellerInquiries(fixture.firstStore().id(), fixture.firstSeller().getId(), null)
            .size());
  }

  @Test
  @DisplayName("판매자는 다른 스토어 문의방에 접근할 수 없다")
  void rejectsOtherStoreInquiryAccess() {
    Fixture fixture = prepareFixture("inquiry-other-store");

    BaseException exception = assertThrows(
        BaseException.class,
        () -> inquiryChatAccessService.getSellerInquiry(
            fixture.firstInquiry().getId(), fixture.secondStore().id()));

    assertEquals(ChatErrorCode.CHAT_PARTICIPANT_FORBIDDEN, exception.getErrorCode());
  }

  private Fixture prepareFixture(String prefix) {
    User firstSeller = saveUser(UserRole.SELLER, prefix + "-seller1");
    User secondSeller = saveUser(UserRole.SELLER, prefix + "-seller2");
    User buyer = saveUser(UserRole.BUYER, prefix + "-buyer");
    StoreResult firstStore = createStore(firstSeller.getId(), prefix + " first");
    StoreResult secondStore = createStore(secondSeller.getId(), prefix + " second");
    Inquiry firstInquiry =
        inquiryOpenService.open(OpenInquiryCommand.of(firstStore.id(), buyer.getId()));
    Inquiry secondInquiry =
        inquiryOpenService.open(OpenInquiryCommand.of(secondStore.id(), buyer.getId()));

    return new Fixture(
        firstSeller, secondSeller, buyer, firstStore, secondStore, firstInquiry, secondInquiry);
  }

  private StoreResult createStore(UUID ownerUserId, String name) {
    return storeService.create(new CreateStoreCommand(
        ownerUserId,
        "P3 " + name,
        null,
        "주문제작 케이크 스토어",
        "010-1234-5678",
        true,
        null,
        "{\"mon\":\"10:00-18:00\"}",
        "{\"leadTimeDays\":3}",
        "서울특별시 중구"));
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

  private Asset saveAsset(UUID uploadedBy, String objectKey) {
    Asset asset =
        Asset.create(UUID.randomUUID(), uploadedBy, objectKey, "image/png", 1024L, objectKey);
    asset.markReady();
    return assetJpaRepository.saveAndFlush(asset);
  }

  private AssetVariant saveVariant(Asset asset, String objectKey) {
    return assetVariantJpaRepository.saveAndFlush(AssetVariant.create(
        asset, AssetVariantType.MEDIUM, objectKey, "image/webp", 640, 640, 512L));
  }

  private record Fixture(
      User firstSeller,
      User secondSeller,
      User buyer,
      StoreResult firstStore,
      StoreResult secondStore,
      Inquiry firstInquiry,
      Inquiry secondInquiry) {}

  @TestConfiguration
  static class InquiryListClockTestConfiguration {

    @Bean
    @Primary
    Clock fixedInquiryListClock() {
      return Clock.fixed(Instant.parse("2026-08-25T03:00:00Z"), ZoneOffset.UTC);
    }
  }
}
