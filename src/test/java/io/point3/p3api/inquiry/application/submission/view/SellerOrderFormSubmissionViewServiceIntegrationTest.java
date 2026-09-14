package io.point3.p3api.inquiry.application.submission.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.point3.p3api.IntegrationTestSupport;
import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.ChatErrorCode;
import io.point3.p3api.exception.code.OrderFormErrorCode;
import io.point3.p3api.inquiry.application.command.OpenInquiryCommand;
import io.point3.p3api.inquiry.application.open.InquiryOpenService;
import io.point3.p3api.inquiry.application.submission.query.SellerOrderFormSubmissionQueryService;
import io.point3.p3api.inquiry.application.submission.result.OrderFormSubmissionResult;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SellerOrderFormSubmissionViewServiceIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private SellerOrderFormSubmissionViewService viewService;

  @Autowired
  private SellerOrderFormSubmissionQueryService queryService;

  @Autowired
  private InquiryOpenService inquiryOpenService;

  @Autowired
  private StoreService storeService;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private InquiryJpaRepository inquiryJpaRepository;

  @Autowired
  private OrderFormTemplateJpaRepository orderFormTemplateJpaRepository;

  @Autowired
  private OrderFormSubmissionJpaRepository orderFormSubmissionJpaRepository;

  @Test
  @DisplayName("판매자 주문서 목록 조회는 제출 주문서를 확인 처리하지 않는다")
  void listDoesNotMarkViewed() {
    Fixture fixture = prepareFixture("list-read");
    OrderFormSubmission submission = saveSubmission(fixture, "초코 케이크");

    List<OrderFormSubmissionResult> results =
        queryService.getSubmissions(fixture.inquiry().getId(), fixture.store().id());

    assertEquals(1, results.size());
    assertEquals(submission.getId(), results.getFirst().id());
    assertNull(refreshedSubmission(submission).getSellerViewedAt());
  }

  @Test
  @DisplayName("판매자는 특정 제출 주문서만 독립적으로 확인 처리한다")
  void marksOnlySelectedSubmissionViewed() {
    Fixture fixture = prepareFixture("selected-read");
    OrderFormSubmission first = saveSubmission(fixture, "초코 케이크 A");
    OrderFormSubmission second = saveSubmission(fixture, "초코 케이크 B");

    OrderFormSubmissionResult result = viewService.markViewed(
        fixture.inquiry().getId(), first.getId(), fixture.store().id());

    assertTrue(result.sellerViewed());
    assertFalse(result.current());
    assertNotNull(refreshedSubmission(first).getSellerViewedAt());
    assertNull(refreshedSubmission(second).getSellerViewedAt());
    assertEquals(InquiryStatus.WAITING, refreshedInquiry(fixture).getStatus());
    assertEquals(second.getId(), refreshedInquiry(fixture).getCurrentOrderFormSubmissionId());
  }

  @Test
  @DisplayName("현재 주문서를 확인하면 문의 상태를 상담중으로 전환한다")
  void marksCurrentSubmissionViewedAndInProgress() {
    Fixture fixture = prepareFixture("current-read");
    OrderFormSubmission first = saveSubmission(fixture, "초코 케이크 A");
    OrderFormSubmission second = saveSubmission(fixture, "초코 케이크 B");

    OrderFormSubmissionResult result = viewService.markViewed(
        fixture.inquiry().getId(), second.getId(), fixture.store().id());

    assertTrue(result.sellerViewed());
    assertTrue(result.current());
    assertNull(refreshedSubmission(first).getSellerViewedAt());
    assertNotNull(refreshedSubmission(second).getSellerViewedAt());
    assertEquals(InquiryStatus.IN_PROGRESS, refreshedInquiry(fixture).getStatus());
  }

  @Test
  @DisplayName("새 현재 주문서가 추가되어도 기존 주문서 확인 상태는 유지된다")
  void keepsViewedStateAfterNewCurrentSubmission() {
    Fixture fixture = prepareFixture("new-current-submission");
    OrderFormSubmission first = saveSubmission(fixture, "초코 케이크 A");
    viewService.markViewed(
        fixture.inquiry().getId(), first.getId(), fixture.store().id());
    Instant firstViewedAt = refreshedSubmission(first).getSellerViewedAt();

    assertEquals(InquiryStatus.IN_PROGRESS, refreshedInquiry(fixture).getStatus());
    OrderFormSubmission second = saveSubmission(fixture, "초코 케이크 B");
    OrderFormSubmission third = saveSubmission(fixture, "초코 케이크 C");

    assertEquals(InquiryStatus.WAITING, refreshedInquiry(fixture).getStatus());
    assertEquals(third.getId(), refreshedInquiry(fixture).getCurrentOrderFormSubmissionId());
    assertEquals(firstViewedAt, refreshedSubmission(first).getSellerViewedAt());
    assertNull(refreshedSubmission(second).getSellerViewedAt());
    assertNull(refreshedSubmission(third).getSellerViewedAt());

    viewService.markViewed(
        fixture.inquiry().getId(), third.getId(), fixture.store().id());

    assertEquals(firstViewedAt, refreshedSubmission(first).getSellerViewedAt());
    assertNull(refreshedSubmission(second).getSellerViewedAt());
    assertNotNull(refreshedSubmission(third).getSellerViewedAt());
    assertEquals(InquiryStatus.IN_PROGRESS, refreshedInquiry(fixture).getStatus());
  }

  @Test
  @DisplayName("이미 확인한 현재 주문서가 접수대기 상태와 불일치하면 확인 요청으로 복구한다")
  void repairsWaitingCurrentSubmission() {
    Fixture fixture = prepareFixture("repair-current");
    OrderFormSubmission submission = saveSubmission(fixture, "초코 케이크");
    viewService.markViewed(
        fixture.inquiry().getId(), submission.getId(), fixture.store().id());
    Instant firstViewedAt = refreshedSubmission(submission).getSellerViewedAt();
    markInquiryWaiting(fixture);

    viewService.markViewed(
        fixture.inquiry().getId(), submission.getId(), fixture.store().id());

    assertEquals(firstViewedAt, refreshedSubmission(submission).getSellerViewedAt());
    assertEquals(InquiryStatus.IN_PROGRESS, refreshedInquiry(fixture).getStatus());
  }

  @Test
  @DisplayName("같은 제출 주문서 확인 요청은 멱등적으로 처리한다")
  void markViewedIsIdempotent() {
    Fixture fixture = prepareFixture("idempotent");
    OrderFormSubmission submission = saveSubmission(fixture, "초코 케이크");

    viewService.markViewed(
        fixture.inquiry().getId(), submission.getId(), fixture.store().id());
    Instant firstViewedAt = refreshedSubmission(submission).getSellerViewedAt();
    viewService.markViewed(
        fixture.inquiry().getId(), submission.getId(), fixture.store().id());

    assertEquals(firstViewedAt, refreshedSubmission(submission).getSellerViewedAt());
  }

  @Test
  @DisplayName("판매자는 다른 스토어 문의의 제출 주문서를 확인할 수 없다")
  void rejectsOtherStoreInquiry() {
    Fixture fixture = prepareFixture("other-store");
    OrderFormSubmission submission = saveSubmission(fixture, "초코 케이크");
    User otherSeller = saveUser(UserRole.SELLER, "other-store-seller");
    StoreResult otherStore = createStore(otherSeller.getId(), "다른 스토어");

    BaseException exception = assertThrows(
        BaseException.class,
        () ->
            viewService.markViewed(fixture.inquiry().getId(), submission.getId(), otherStore.id()));

    assertEquals(ChatErrorCode.CHAT_PARTICIPANT_FORBIDDEN, exception.getErrorCode());
  }

  @Test
  @DisplayName("판매자는 같은 스토어의 다른 문의 제출 주문서를 확인할 수 없다")
  void rejectsOtherInquirySubmission() {
    Fixture fixture = prepareFixture("other-inquiry");
    User otherBuyer = saveUser(UserRole.BUYER, "other-inquiry-buyer");
    Inquiry otherInquiry =
        inquiryOpenService.open(OpenInquiryCommand.of(fixture.store().id(), otherBuyer.getId()));
    OrderFormSubmission otherSubmission =
        saveSubmission(fixture.template(), otherInquiry, otherBuyer);

    BaseException exception = assertThrows(
        BaseException.class,
        () -> viewService.markViewed(
            fixture.inquiry().getId(), otherSubmission.getId(), fixture.store().id()));

    assertEquals(OrderFormErrorCode.ORDER_FORM_NOT_FOUND, exception.getErrorCode());
  }

  private Fixture prepareFixture(String prefix) {
    User seller = saveUser(UserRole.SELLER, prefix + "-seller");
    User buyer = saveUser(UserRole.BUYER, prefix + "-buyer");
    StoreResult store = createStore(seller.getId(), prefix + "-store");
    OrderFormTemplate template =
        orderFormTemplateJpaRepository.saveAndFlush(OrderFormTemplate.create(store.id(), "기본 주문서"));
    Inquiry inquiry = inquiryOpenService.open(OpenInquiryCommand.of(store.id(), buyer.getId()));
    return new Fixture(seller, buyer, store, template, inquiry);
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

  private StoreResult createStore(UUID sellerId, String name) {
    return storeService.create(new CreateStoreCommand(
        sellerId,
        name,
        null,
        "주문제작 케이크 스토어",
        "010-1234-5678",
        true,
        null,
        "{\"mon\":\"10:00-18:00\"}",
        "{\"leadTimeDays\":3}",
        "서울특별시 중구"));
  }

  private OrderFormSubmission saveSubmission(Fixture fixture, String menuName) {
    return saveSubmission(fixture.template(), fixture.inquiry(), fixture.buyer(), menuName);
  }

  private OrderFormSubmission saveSubmission(
      OrderFormTemplate template, Inquiry inquiry, User buyer) {
    return saveSubmission(template, inquiry, buyer, "초코 케이크");
  }

  private OrderFormSubmission saveSubmission(
      OrderFormTemplate template, Inquiry inquiry, User buyer, String menuName) {
    OrderFormSubmission saved =
        orderFormSubmissionJpaRepository.saveAndFlush(OrderFormSubmission.create(
            inquiry.getId(),
            template.getId(),
            buyer.getId(),
            LocalDate.parse("2030-08-30"),
            LocalTime.parse("13:30"),
            "[{\"label\":\"메뉴명\",\"value\":\"" + menuName + "\"}]",
            "[]",
            true));
    inquiry.reopenSellerOnSubmission(saved.getId());
    inquiryJpaRepository.saveAndFlush(inquiry);
    return saved;
  }

  private void markInquiryWaiting(Fixture fixture) {
    Inquiry inquiry = refreshedInquiry(fixture);
    inquiry.markWaiting();
    inquiryJpaRepository.saveAndFlush(inquiry);
  }

  private Inquiry refreshedInquiry(Fixture fixture) {
    return inquiryJpaRepository.findById(fixture.inquiry().getId()).orElseThrow();
  }

  private OrderFormSubmission refreshedSubmission(OrderFormSubmission submission) {
    return orderFormSubmissionJpaRepository.findById(submission.getId()).orElseThrow();
  }

  private record Fixture(
      User seller, User buyer, StoreResult store, OrderFormTemplate template, Inquiry inquiry) {}
}
