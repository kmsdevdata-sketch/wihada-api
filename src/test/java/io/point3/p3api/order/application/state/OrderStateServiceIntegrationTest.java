package io.point3.p3api.order.application.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.point3.p3api.IntegrationTestSupport;
import io.point3.p3api.chat.domain.type.ChatTimelineItemType;
import io.point3.p3api.chat.infrastructure.persistence.ChatTimelineItemJpaRepository;
import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.OrderErrorCode;
import io.point3.p3api.inquiry.domain.entity.Inquiry;
import io.point3.p3api.inquiry.domain.entity.OrderFormSubmission;
import io.point3.p3api.inquiry.domain.type.InquiryStatus;
import io.point3.p3api.inquiry.infrastructure.persistence.InquiryJpaRepository;
import io.point3.p3api.inquiry.infrastructure.persistence.OrderFormSubmissionJpaRepository;
import io.point3.p3api.notification.domain.type.NotificationType;
import io.point3.p3api.notification.infrastructure.persistence.NotificationJpaRepository;
import io.point3.p3api.order.application.query.order.OrderQueryUseCase;
import io.point3.p3api.order.application.refund.OrderRefundCalculationBasis;
import io.point3.p3api.order.application.result.OrderDetailResult;
import io.point3.p3api.order.application.result.OrderRefundQuoteResult;
import io.point3.p3api.order.application.result.OrderResult;
import io.point3.p3api.order.domain.entity.Order;
import io.point3.p3api.order.domain.entity.OrderConfirmation;
import io.point3.p3api.order.domain.type.OrderStatus;
import io.point3.p3api.order.infrastructure.persistence.OrderConfirmationJpaRepository;
import io.point3.p3api.order.infrastructure.persistence.OrderJpaRepository;
import io.point3.p3api.order.infrastructure.persistence.OrderStatusHistoryJpaRepository;
import io.point3.p3api.orderform.domain.entity.OrderFormTemplate;
import io.point3.p3api.orderform.infrastructure.persistence.OrderFormTemplateJpaRepository;
import io.point3.p3api.payment.application.port.Point3PaymentPort;
import io.point3.p3api.payment.application.port.Point3PaymentException;
import io.point3.p3api.payment.application.port.Point3RefundResult;
import io.point3.p3api.payment.application.port.Point3RefundStatusResult;
import io.point3.p3api.payment.application.result.Point3CaptureResult;
import io.point3.p3api.payment.application.result.Point3PaymentSession;
import io.point3.p3api.payment.domain.entity.PaymentAttempt;
import io.point3.p3api.payment.domain.entity.Refund;
import io.point3.p3api.payment.domain.type.PaymentAttemptStatus;
import io.point3.p3api.payment.domain.type.RefundCompletionMethod;
import io.point3.p3api.payment.domain.type.RefundOutcome;
import io.point3.p3api.payment.domain.type.RefundStatus;
import io.point3.p3api.payment.infrastructure.persistence.PaymentAttemptJpaRepository;
import io.point3.p3api.payment.infrastructure.persistence.RefundJpaRepository;
import io.point3.p3api.store.domain.entity.Store;
import io.point3.p3api.store.domain.entity.StoreRefundPolicy;
import io.point3.p3api.store.infrastructure.persistence.StoreJpaRepository;
import io.point3.p3api.store.infrastructure.persistence.StoreRefundPolicyJpaRepository;
import io.point3.p3api.user.domain.entity.User;
import io.point3.p3api.user.domain.type.SignupProvider;
import io.point3.p3api.user.domain.type.UserRole;
import io.point3.p3api.user.infrastructure.persistence.UserJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(OrderStateServiceIntegrationTest.RefundTestConfiguration.class)
class OrderStateServiceIntegrationTest extends IntegrationTestSupport {

  private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

  @Autowired
  private OrderQueryUseCase orderQueryUseCase;

  @Autowired
  private OrderStateUseCase orderStateUseCase;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private StoreJpaRepository storeJpaRepository;

  @Autowired
  private StoreRefundPolicyJpaRepository storeRefundPolicyJpaRepository;

  @Autowired
  private InquiryJpaRepository inquiryJpaRepository;

  @Autowired
  private OrderConfirmationJpaRepository orderConfirmationJpaRepository;

  @Autowired
  private OrderFormTemplateJpaRepository orderFormTemplateJpaRepository;

  @Autowired
  private OrderFormSubmissionJpaRepository orderFormSubmissionJpaRepository;

  @Autowired
  private PaymentAttemptJpaRepository paymentAttemptJpaRepository;

  @Autowired
  private OrderJpaRepository orderJpaRepository;

  @Autowired
  private OrderStatusHistoryJpaRepository orderStatusHistoryJpaRepository;

  @Autowired
  private RefundJpaRepository refundJpaRepository;

  @Autowired
  private NotificationJpaRepository notificationJpaRepository;

  @Autowired
  private ChatTimelineItemJpaRepository chatTimelineItemJpaRepository;

  @Autowired
  private RecordingPoint3PaymentPort point3PaymentPort;

  @Autowired
  private MutableClock testClock;

  @BeforeEach
  void resetPoint3PaymentPort() {
    point3PaymentPort.reset();
    testClock.setInstant(todayAtNoon());
  }

  @TestConfiguration
  static class RefundTestConfiguration {
    @Bean
    @Primary
    RecordingPoint3PaymentPort point3PaymentPort() {
      return new RecordingPoint3PaymentPort();
    }

    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock(Instant.now());
    }
  }

  static class MutableClock extends Clock {
    private Instant instant;

    MutableClock(Instant instant) {
      this.instant = instant;
    }

    void setInstant(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }

  static class RecordingPoint3PaymentPort implements Point3PaymentPort {

    private int refundCallCount;
    private long lastRefundAmount;
    private Point3RefundResult refundResult = Point3RefundResult.completed("ref-point3");
    private Point3RefundStatusResult statusResult = new Point3RefundStatusResult(
        "pymt_sess-test", "fullyRefunded", 0, false, List.of(refundResult));
    private Point3RefundStatusResult resumeResult = statusResult;
    private boolean failRefundRequest;

    @Override
    public Point3PaymentSession createSession(
        long amount, String productName, String merchantName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Point3CaptureResult capture(String sessionId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Point3CaptureResult getSession(String sessionId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Point3RefundResult refund(String sessionId, long amount, String reason, String key) {
      refundCallCount++;
      lastRefundAmount = amount;
      if (failRefundRequest) {
        throw new Point3PaymentException("POINT3_REFUND", "connection lost");
      }
      return refundResult;
    }

    @Override
    public Point3RefundStatusResult getRefundStatus(String sessionId) {
      return statusResult;
    }

    @Override
    public Point3RefundStatusResult resumeRefund(String sessionId) {
      return resumeResult;
    }

    void reset() {
      refundCallCount = 0;
      lastRefundAmount = 0;
      refundResult = Point3RefundResult.completed("ref-point3");
      statusResult = new Point3RefundStatusResult(
          "pymt_sess-test", "fullyRefunded", 0, false, List.of(refundResult));
      resumeResult = statusResult;
      failRefundRequest = false;
    }
  }

  @Test
  @DisplayName("주문 상세은 결제시도와 환불 내역을 함께 반환한다")
  void getsOrderDetailWithPaymentAndRefunds() {
    Fixture fixture = prepareFixture("order-detail");
    Refund refund = Refund.create(
        fixture.order().getId(),
        fixture.paymentAttempt().getId(),
        fixture.seller().getId(),
        fixture.order().getPaidAmount(),
        "판매자 취소");
    refund.complete(Instant.parse("2026-09-01T01:00:00Z"));
    refundJpaRepository.saveAndFlush(refund);

    OrderDetailResult buyerDetail =
        orderQueryUseCase.getBuyerOrder(fixture.order().getId(), fixture.buyer().getId());
    OrderDetailResult sellerDetail = orderQueryUseCase.getSellerOrder(
        fixture.order().getId(), fixture.store().getId());

    assertEquals(fixture.order().getId(), buyerDetail.order().id());
    assertEquals(fixture.inquiry().getId(), buyerDetail.order().inquiryId());
    assertEquals(PaymentAttemptStatus.SUCCEEDED, buyerDetail.paymentAttempt().status());
    assertEquals(1, buyerDetail.refunds().size());
    assertEquals(RefundStatus.COMPLETED, buyerDetail.refunds().get(0).status());
    assertEquals(fixture.order().getId(), sellerDetail.order().id());
    assertEquals(2, sellerDetail.optionRows().size());
    assertEquals("메뉴명", sellerDetail.optionRows().get(0).label());
    assertEquals("초코 케이크", sellerDetail.optionRows().get(0).value());
    assertEquals("토핑", sellerDetail.optionRows().get(1).label());
    assertEquals("딸기", sellerDetail.optionRows().get(1).value());
  }

  @Test
  @DisplayName("구매자는 주문 취소를 요청하고 판매자는 전체 환불 완료 처리한다")
  void requestsCancelAndRefunds() {
    Fixture fixture = prepareFixture("order-refund");

    OrderResult cancelRequested = orderStateUseCase.requestRefund(
        RequestOrderRefundCommand.of(fixture.order().getId(), fixture.buyer().getId(), "픽업 일정 변경"));
    OrderDetailResult refunded = orderStateUseCase.refund(RefundOrderCommand.of(
        fixture.order().getId(),
        fixture.store().getId(),
        fixture.seller().getId(),
        "구매자 취소 요청 승인"));

    assertEquals(OrderStatus.REFUND_REQUESTED, cancelRequested.status());
    assertEquals("픽업 일정 변경", cancelRequested.refundReason());
    assertNotNull(cancelRequested.refundRequestedAt());
    assertEquals(OrderStatus.REFUNDED, refunded.order().status());
    assertEquals("구매자 취소 요청 승인", refunded.order().refundReason());
    assertEquals(1, refunded.refunds().size());
    assertEquals(fixture.order().getPaidAmount(), refunded.refunds().get(0).amount());
    assertEquals(RefundStatus.COMPLETED, refunded.refunds().get(0).status());
    assertEquals(RefundOutcome.COMPLETED, refunded.refunds().get(0).outcome());
    assertEquals(
        1,
        notificationJpaRepository
            .findAllByUserIdOrderByCreatedAtDesc(fixture.seller().getId())
            .stream()
            .filter(
                notification -> notification.getType() == NotificationType.ORDER_REFUND_REQUESTED)
            .count());
    assertEquals(
        1,
        notificationJpaRepository
            .findAllByUserIdOrderByCreatedAtDesc(fixture.buyer().getId())
            .stream()
            .filter(notification -> notification.getType() == NotificationType.ORDER_REFUNDED)
            .count());
    assertEquals(1, timelineCount(
        fixture.inquiry().getId(), ChatTimelineItemType.ORDER_REFUND_REQUESTED));
    assertEquals(1, timelineCount(
        fixture.inquiry().getId(), ChatTimelineItemType.ORDER_REFUND_COMPLETED));
  }

  @Test
  @DisplayName("판매자 환불은 사유가 없으면 기본 사유로 처리한다")
  void refundsWithDefaultReason() {
    Fixture fixture = prepareFixture("order-refund-default");
    requestRefund(fixture);

    OrderDetailResult refunded = orderStateUseCase.refund(RefundOrderCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId(), null));

    assertEquals(OrderStatus.REFUNDED, refunded.order().status());
    assertEquals("판매자 환불 처리", refunded.order().refundReason());
    assertEquals("판매자 환불 처리", refunded.refunds().getFirst().reason());
  }

  @Test
  @DisplayName("스토어 정책의 환불률로 부분 환불하고 Point3에 같은 금액을 요청한다")
  void refundsPartialAmountFromStorePolicy() {
    Fixture fixture = prepareFixture(
        "order-partial-refund",
        6,
        List.of(new PolicyRule(7, 100), new PolicyRule(5, 80), new PolicyRule(3, 50)));
    requestRefund(fixture);

    OrderDetailResult refunded = orderStateUseCase.refund(RefundOrderCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId(), "정책 부분 환불"));

    assertEquals(OrderStatus.REFUNDED, refunded.order().status());
    assertEquals(32_800, refunded.refunds().getFirst().amount());
    assertEquals(80, refunded.refunds().getFirst().refundRate());
    assertEquals(1, point3PaymentPort.refundCallCount);
    assertEquals(32_800, point3PaymentPort.lastRefundAmount);
  }

  @Test
  @DisplayName("구매자 환불 요청 주문은 판매자가 늦게 처리해도 요청 시각 기준 환불률을 적용한다")
  void refundsRequestedOrderUsingRefundRequestedAt() {
    Instant requestedAt = todayAtNoon();
    testClock.setInstant(requestedAt);
    Fixture fixture = prepareFixture(
        "order-refund-requested-at",
        7,
        List.of(new PolicyRule(7, 100), new PolicyRule(5, 80), new PolicyRule(3, 50)));
    OrderResult requested = requestRefund(fixture);
    OrderRefundQuoteResult quote = orderQueryUseCase.getSellerOrderRefundQuote(
        fixture.order().getId(), fixture.store().getId());

    testClock.setInstant(requestedAt.plus(2, ChronoUnit.DAYS));
    OrderDetailResult refunded = orderStateUseCase.refund(RefundOrderCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId(), "환불 요청 승인"));

    assertEquals(requested.refundRequestedAt(), quote.calculationBaseAt());
    assertEquals(OrderRefundCalculationBasis.REFUND_REQUESTED_AT, quote.calculationBasis());
    assertEquals(41_000, quote.refundAmount());
    assertEquals(100, quote.refundRate());
    assertEquals(41_000, refunded.refunds().getFirst().amount());
    assertEquals(100, refunded.refunds().getFirst().refundRate());
    assertEquals(41_000, point3PaymentPort.lastRefundAmount);
  }

  @Test
  @DisplayName("환불 quote 조회는 Refund 생성과 주문 상태, 타임라인, 알림을 변경하지 않는다")
  void quotesRefundWithoutSideEffects() {
    Fixture fixture = prepareFixture(
        "order-refund-quote-side-effect",
        7,
        List.of(new PolicyRule(7, 100), new PolicyRule(5, 80)));
    requestRefund(fixture);
    long refundCount = refundJpaRepository.count();
    long timelineCount = chatTimelineItemJpaRepository.count();
    long notificationCount = notificationJpaRepository.count();

    OrderRefundQuoteResult quote = orderQueryUseCase.getSellerOrderRefundQuote(
        fixture.order().getId(), fixture.store().getId());
    Order found = orderJpaRepository.findById(fixture.order().getId()).orElseThrow();

    assertEquals(41_000, quote.refundAmount());
    assertEquals(OrderStatus.REFUND_REQUESTED, found.getStatus());
    assertEquals(refundCount, refundJpaRepository.count());
    assertEquals(timelineCount, chatTimelineItemJpaRepository.count());
    assertEquals(notificationCount, notificationJpaRepository.count());
  }

  @Test
  @DisplayName("다른 스토어의 주문은 환불 quote를 조회할 수 없다")
  void rejectsRefundQuoteForOtherStore() {
    Fixture fixture = prepareFixture("order-refund-quote-owner");
    Fixture other = prepareFixture("order-refund-quote-other");

    BaseException exception = assertThrows(
        BaseException.class,
        () -> orderQueryUseCase.getSellerOrderRefundQuote(
            fixture.order().getId(), other.store().getId()));

    assertEquals(OrderErrorCode.ORDER_NOT_FOUND, exception.getErrorCode());
  }

  @Test
  @DisplayName("환불 불가능 상태의 주문은 환불 quote를 조회할 수 없다")
  void rejectsRefundQuoteForNotRefundableOrder() {
    Fixture fixture = prepareFixture("order-refund-quote-picked-up");
    orderStateUseCase.pickUp(
        CompleteOrderPickupCommand.of(fixture.order().getId(), fixture.store().getId()));

    BaseException exception = assertThrows(
        BaseException.class,
        () -> orderQueryUseCase.getSellerOrderRefundQuote(
            fixture.order().getId(), fixture.store().getId()));

    assertEquals(OrderErrorCode.ORDER_STATUS_FORBIDDEN, exception.getErrorCode());
  }

  @Test
  @DisplayName("적용할 환불정책이 없으면 0원으로 완료하고 Point3를 호출하지 않는다")
  void completesZeroAmountRefundWithoutPoint3Request() {
    Fixture fixture = prepareFixture(
        "order-zero-refund",
        2,
        List.of(new PolicyRule(7, 100), new PolicyRule(5, 80), new PolicyRule(3, 50)));
    requestRefund(fixture);

    OrderDetailResult refunded = orderStateUseCase.refund(RefundOrderCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId(), "환불 가능 기간 경과"));

    assertEquals(OrderStatus.REFUNDED, refunded.order().status());
    assertEquals(0, refunded.refunds().getFirst().amount());
    assertEquals(0, refunded.refunds().getFirst().refundRate());
    assertEquals(RefundStatus.COMPLETED, refunded.refunds().getFirst().status());
    assertEquals(0, point3PaymentPort.refundCallCount);
  }

  @Test
  @DisplayName("완료된 주문 환불은 다시 요청할 수 없다")
  void rejectsDuplicateRefund() {
    Fixture fixture = prepareFixture("order-duplicate-refund");
    requestRefund(fixture);
    RefundOrderCommand command = RefundOrderCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId(), "중복 환불");
    orderStateUseCase.refund(command);

    BaseException exception =
        assertThrows(BaseException.class, () -> orderStateUseCase.refund(command));

    assertEquals(OrderErrorCode.ORDER_REFUND_ALREADY_PROCESSED, exception.getErrorCode());
    assertEquals(1, point3PaymentPort.refundCallCount);
  }

  @Test
  @DisplayName("Point3 EOB 차단은 재시도 가능한 실패로 남기고 같은 주문의 재시도를 허용한다")
  void recordsRetryableRefundAndAllowsRetry() {
    Fixture fixture = prepareFixture("order-refund-retry");
    requestRefund(fixture);
    RefundOrderCommand command = RefundOrderCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId(), "환불 재시도");
    point3PaymentPort.refundResult = Point3RefundResult.retryable(
        null, "EOB_WINDOW_BLOCKED", "현재 취소 차단 시간대입니다.", null);

    OrderDetailResult failed = orderStateUseCase.refund(command);

    assertEquals(OrderStatus.REFUND_REQUESTED, failed.order().status());
    assertEquals(RefundStatus.FAILED, failed.refunds().getFirst().status());
    assertEquals(RefundOutcome.RETRYABLE, failed.refunds().getFirst().outcome());
    assertEquals("EOB_WINDOW_BLOCKED", failed.refunds().getFirst().failureCode());
    assertEquals(0, refundCompletedNotificationCount(fixture.buyer().getId()));
    assertEquals(0, refundFailedNotificationCount(fixture.buyer().getId()));

    point3PaymentPort.refundResult = Point3RefundResult.completed("ref-point3-retry");
    OrderDetailResult retried = orderStateUseCase.refund(command);

    assertEquals(OrderStatus.REFUNDED, retried.order().status());
    assertEquals(2, retried.refunds().size());
    assertEquals(
        1,
        retried.refunds().stream()
            .filter(refund -> refund.status() == RefundStatus.COMPLETED)
            .count());
    assertEquals(2, point3PaymentPort.refundCallCount);
  }

  @Test
  @DisplayName("정산 마감 환불 거절은 수동 환불 필요 결과로 남기고 주문을 환불 완료하지 않는다")
  void recordsManualRequiredRefundWhenSettlementDeadlineExceeded() {
    Fixture fixture = prepareFixture("order-refund-manual-required");
    orderStateUseCase.requestRefund(RequestOrderRefundCommand.of(
        fixture.order().getId(), fixture.buyer().getId(), "취소 요청"));
    point3PaymentPort.refundResult = Point3RefundResult.manualRequired(
        null,
        "SETTLEMENT_DEADLINE_EXCEEDED",
        "정산 마감일이 지나 환불을 요청할 수 없습니다",
        "{\"paymentSessionId\":\"pymt_sess-test\"}");

    OrderDetailResult result = orderStateUseCase.refund(RefundOrderCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId(), "판매자 환불"));

    assertEquals(OrderStatus.REFUND_REQUESTED, result.order().status());
    assertEquals(RefundStatus.FAILED, result.refunds().getFirst().status());
    assertEquals(RefundOutcome.MANUAL_REQUIRED, result.refunds().getFirst().outcome());
    assertEquals(
        "SETTLEMENT_DEADLINE_EXCEEDED", result.refunds().getFirst().failureCode());
    assertEquals(0, refundCompletedNotificationCount(fixture.buyer().getId()));
    assertEquals(0, refundFailedNotificationCount(fixture.buyer().getId()));
  }

  @Test
  @DisplayName("판매자는 수동 환불 필요 건을 특정 refundId로 수동 완료한다")
  void completesManualRequiredRefund() {
    Fixture fixture = prepareFixture("order-refund-manual-complete");
    OrderDetailResult manualRequired = createManualRequiredRefund(fixture);
    var targetRefund = manualRequired.refunds().getFirst();

    OrderDetailResult result =
        orderStateUseCase.completeManualRefund(CompleteManualOrderRefundCommand.of(
            fixture.order().getId(),
            targetRefund.refundId(),
            fixture.store().getId(),
            fixture.seller().getId()));

    var completedRefund = result.refunds().getFirst();
    assertEquals(OrderStatus.REFUNDED, result.order().status());
    assertEquals(RefundStatus.COMPLETED, completedRefund.status());
    assertEquals(RefundOutcome.COMPLETED, completedRefund.outcome());
    assertEquals(RefundCompletionMethod.MANUAL, completedRefund.completionMethod());
    assertEquals(fixture.seller().getId(), completedRefund.completedBy());
    assertEquals("SETTLEMENT_DEADLINE_EXCEEDED", completedRefund.failureCode());
    assertEquals(
        "정산 마감일이 지나 환불을 요청할 수 없습니다", completedRefund.failureMessage());
    assertEquals("{\"paymentSessionId\":\"pymt_sess-test\"}", completedRefund.failureDetails());
    assertNotNull(completedRefund.failedAt());
    assertEquals(1, refundCompletedNotificationCount(fixture.buyer().getId()));
    assertEquals(1, timelineCount(
        fixture.inquiry().getId(), ChatTimelineItemType.ORDER_REFUND_COMPLETED));
    assertEquals(
        1,
        orderStatusHistoryJpaRepository
            .findAllByOrderIdOrderByCreatedAtDesc(fixture.order().getId())
            .stream()
            .filter(history -> history.getNewStatus() == OrderStatus.REFUNDED)
            .count());
  }

  @Test
  @DisplayName("다른 주문의 refundId로 수동 환불 완료할 수 없다")
  void rejectsManualRefundCompletionWithOtherOrderRefund() {
    Fixture fixture = prepareFixture("order-refund-manual-other");
    Fixture other = prepareFixture("order-refund-manual-other-target");
    OrderDetailResult manualRequired = createManualRequiredRefund(other);

    BaseException exception = assertThrows(
        BaseException.class,
        () -> orderStateUseCase.completeManualRefund(CompleteManualOrderRefundCommand.of(
            fixture.order().getId(),
            manualRequired.refunds().getFirst().refundId(),
            fixture.store().getId(),
            fixture.seller().getId())));

    assertEquals(OrderErrorCode.ORDER_NOT_FOUND, exception.getErrorCode());
  }

  @Test
  @DisplayName("재시도 가능 실패는 수동 환불 완료할 수 없다")
  void rejectsRetryableManualRefundCompletion() {
    Fixture fixture = prepareFixture("order-refund-manual-retryable");
    requestRefund(fixture);
    point3PaymentPort.refundResult =
        Point3RefundResult.retryable(null, "EOB_WINDOW_BLOCKED", "현재 취소 차단 시간대입니다.", null);
    OrderDetailResult retryable = orderStateUseCase.refund(RefundOrderCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId(), "환불 처리"));

    BaseException exception = assertThrows(
        BaseException.class,
        () -> orderStateUseCase.completeManualRefund(CompleteManualOrderRefundCommand.of(
            fixture.order().getId(),
            retryable.refunds().getFirst().refundId(),
            fixture.store().getId(),
            fixture.seller().getId())));

    assertEquals(OrderErrorCode.ORDER_STATUS_FORBIDDEN, exception.getErrorCode());
  }

  @Test
  @DisplayName("자동 완료된 환불은 수동 환불 완료할 수 없다")
  void rejectsManualRefundCompletionAfterAutomaticCompletion() {
    Fixture fixture = prepareFixture("order-refund-manual-after-auto");
    requestRefund(fixture);
    OrderDetailResult completed = orderStateUseCase.refund(RefundOrderCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId(), "환불 처리"));

    BaseException exception = assertThrows(
        BaseException.class,
        () -> orderStateUseCase.completeManualRefund(CompleteManualOrderRefundCommand.of(
            fixture.order().getId(),
            completed.refunds().getFirst().refundId(),
            fixture.store().getId(),
            fixture.seller().getId())));

    assertEquals(OrderErrorCode.ORDER_STATUS_FORBIDDEN, exception.getErrorCode());
  }

  @Test
  @DisplayName("수동 환불 완료 중복 요청은 같은 상세 결과를 반환한다")
  void returnsManualCompletedRefundOnDuplicateCompletion() {
    Fixture fixture = prepareFixture("order-refund-manual-duplicate");
    OrderDetailResult manualRequired = createManualRequiredRefund(fixture);
    CompleteManualOrderRefundCommand command = CompleteManualOrderRefundCommand.of(
        fixture.order().getId(),
        manualRequired.refunds().getFirst().refundId(),
        fixture.store().getId(),
        fixture.seller().getId());

    orderStateUseCase.completeManualRefund(command);
    OrderDetailResult duplicate = orderStateUseCase.completeManualRefund(command);

    assertEquals(OrderStatus.REFUNDED, duplicate.order().status());
    assertEquals(RefundCompletionMethod.MANUAL, duplicate.refunds().getFirst().completionMethod());
    assertEquals(1, refundCompletedNotificationCount(fixture.buyer().getId()));
    assertEquals(1, timelineCount(
        fixture.inquiry().getId(), ChatTimelineItemType.ORDER_REFUND_COMPLETED));
  }

  @Test
  @DisplayName("Point3 결과 미확정은 환불과 주문을 처리 중으로 남기고 실패 알림을 보내지 않는다")
  void keepsProcessingWhenRefundResultIsUncertain() {
    Fixture fixture = prepareFixture("order-refund-processing");
    requestRefund(fixture);
    point3PaymentPort.refundResult = Point3RefundResult.processing(
        "ref-processing",
        "REFUND_TEMPORARY_UNAVAILABLE",
        "환불 결과가 아직 확정되지 않았습니다.",
        "{\"refundEntryId\":\"ref-processing\"}");

    OrderDetailResult result = orderStateUseCase.refund(RefundOrderCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId(), "결과 미확정"));

    assertEquals(OrderStatus.REFUND_REQUESTED, result.order().status());
    assertEquals(RefundStatus.PROCESSING, result.refunds().getFirst().status());
    assertEquals(RefundOutcome.PROCESSING, result.refunds().getFirst().outcome());
    assertEquals("ref-processing", result.refunds().getFirst().providerRefundId());
    assertEquals(0, refundCompletedNotificationCount(fixture.buyer().getId()));
    assertEquals(0, refundFailedNotificationCount(fixture.buyer().getId()));
  }

  @Test
  @DisplayName("처리 중 환불 재요청은 새 환불 요청을 보내지 않고 상태 조회로 완료 처리한다")
  void refreshesProcessingRefundWithoutDuplicateRequest() {
    Fixture fixture = prepareFixture("order-refund-processing-duplicate");
    requestRefund(fixture);
    point3PaymentPort.refundResult = Point3RefundResult.processing(
        "ref-processing", "REFUND_TEMPORARY_UNAVAILABLE", "처리 중", null);

    orderStateUseCase.refund(RefundOrderCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId(), "처리 중"));

    point3PaymentPort.refundResult = Point3RefundResult.completed("unused");
    point3PaymentPort.statusResult = new Point3RefundStatusResult(
        "pymt_sess-test",
        "processing",
        41000,
        false,
        List.of(Point3RefundResult.processing("ref-processing", null, null, null)));
    point3PaymentPort.resumeResult = new Point3RefundStatusResult(
        "pymt_sess-test",
        "fullyRefunded",
        0,
        false,
        List.of(Point3RefundResult.completed("ref-processing")));

    OrderDetailResult result = orderStateUseCase.refund(RefundOrderCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId(), "재요청"));

    assertEquals(OrderStatus.REFUNDED, result.order().status());
    assertEquals(1, result.refunds().size());
    assertEquals(1, point3PaymentPort.refundCallCount);
  }

  @Test
  @DisplayName("처리 중 환불 refresh가 원인 없는 처리 중 응답을 받아도 기존 Point3 원인을 보존한다")
  void preservesProcessingRefundFailureCauseOnRefresh() {
    Fixture fixture = prepareFixture("order-refund-processing-cause");
    requestRefund(fixture);
    point3PaymentPort.refundResult = Point3RefundResult.processing(
        "ref-processing",
        "REFUND_TEMPORARY_UNAVAILABLE",
        "환불 결과가 아직 확정되지 않았습니다.",
        "{\"refundEntryId\":\"ref-processing\"}");

    orderStateUseCase.refund(RefundOrderCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId(), "처리 중"));

    point3PaymentPort.statusResult = new Point3RefundStatusResult(
        "pymt_sess-test",
        "processing",
        41000,
        false,
        List.of(Point3RefundResult.processing("ref-processing", null, null, null)));
    point3PaymentPort.resumeResult = new Point3RefundStatusResult(
        "pymt_sess-test",
        "processing",
        41000,
        false,
        List.of(Point3RefundResult.processing("ref-processing", null, null, null)));

    OrderDetailResult result = orderStateUseCase.refreshRefund(RefreshOrderRefundCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId()));

    assertEquals(OrderStatus.REFUND_REQUESTED, result.order().status());
    assertEquals(RefundStatus.PROCESSING, result.refunds().getFirst().status());
    assertEquals("REFUND_TEMPORARY_UNAVAILABLE", result.refunds().getFirst().failureCode());
    assertEquals(
        "환불 결과가 아직 확정되지 않았습니다.", result.refunds().getFirst().failureMessage());
    assertEquals(
        "{\"refundEntryId\":\"ref-processing\"}", result.refunds().getFirst().failureDetails());
  }

  @Test
  @DisplayName("provider id가 없는 처리 중 환불은 Point3 환불 엔트리가 여러 개면 완료로 오판하지 않는다")
  void keepsProcessingWhenPoint3RefundMatchIsAmbiguous() {
    Fixture fixture = prepareFixture("order-refund-ambiguous");
    requestRefund(fixture);
    point3PaymentPort.refundResult = Point3RefundResult.processing(
        null, "POINT3_REFUND_REQUEST_LOST", "환불 요청 응답을 확인하지 못했습니다.", null);

    orderStateUseCase.refund(RefundOrderCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId(), "응답 유실"));

    point3PaymentPort.statusResult = new Point3RefundStatusResult(
        "pymt_sess-test",
        "fullyRefunded",
        0,
        false,
        List.of(
            Point3RefundResult.completed("ref-first"),
            Point3RefundResult.completed("ref-second")));
    point3PaymentPort.resumeResult = point3PaymentPort.statusResult;

    OrderDetailResult result = orderStateUseCase.refreshRefund(RefreshOrderRefundCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId()));

    assertEquals(OrderStatus.REFUND_REQUESTED, result.order().status());
    assertEquals(RefundStatus.PROCESSING, result.refunds().getFirst().status());
    assertEquals("POINT3_REFUND_MATCH_AMBIGUOUS", result.refunds().getFirst().failureCode());
    assertEquals(0, refundCompletedNotificationCount(fixture.buyer().getId()));
  }

  @Test
  @DisplayName("Point3 네트워크 오류는 처리 중으로 남기고 refresh에서 상태 조회 결과로 완료한다")
  void refreshesAfterNetworkError() {
    Fixture fixture = prepareFixture("order-refund-network");
    requestRefund(fixture);
    point3PaymentPort.failRefundRequest = true;

    OrderDetailResult processing = orderStateUseCase.refund(RefundOrderCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId(), "네트워크 오류"));

    assertEquals(OrderStatus.REFUND_REQUESTED, processing.order().status());
    assertEquals(RefundStatus.PROCESSING, processing.refunds().getFirst().status());

    point3PaymentPort.statusResult = new Point3RefundStatusResult(
        "pymt_sess-test",
        "fullyRefunded",
        0,
        false,
        List.of(Point3RefundResult.completed("ref-network")));

    OrderDetailResult refreshed = orderStateUseCase.refreshRefund(RefreshOrderRefundCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId()));

    assertEquals(OrderStatus.REFUNDED, refreshed.order().status());
    assertEquals(RefundStatus.COMPLETED, refreshed.refunds().getFirst().status());
    assertEquals("ref-network", refreshed.refunds().getFirst().providerRefundId());
    assertEquals(1, refundCompletedNotificationCount(fixture.buyer().getId()));
  }

  @Test
  @DisplayName("PAID 상태의 판매자 직접 환불은 현재 처리 시각 기준으로 계산한다")
  void refundsPaidOrderUsingCurrentTime() {
    Fixture fixture = prepareFixture(
        "order-direct-refund",
        5,
        List.of(new PolicyRule(7, 100), new PolicyRule(5, 80), new PolicyRule(3, 50)));

    OrderRefundQuoteResult quote = orderQueryUseCase.getSellerOrderRefundQuote(
        fixture.order().getId(), fixture.store().getId());
    OrderDetailResult refunded = orderStateUseCase.refund(RefundOrderCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId(), "직접 환불"));

    assertEquals(OrderRefundCalculationBasis.CURRENT_TIME, quote.calculationBasis());
    assertEquals(80, quote.refundRate());
    assertEquals(32_800, quote.refundAmount());
    assertEquals(OrderStatus.REFUNDED, refunded.order().status());
    assertEquals(32_800, refunded.refunds().getFirst().amount());
    assertEquals(80, refunded.refunds().getFirst().refundRate());
    assertEquals(1, point3PaymentPort.refundCallCount);
    assertEquals(32_800, point3PaymentPort.lastRefundAmount);
  }

  @Test
  @DisplayName("판매자는 결제완료 주문을 픽업완료로 변경한다")
  void picksUpPaidOrder() {
    Fixture fixture = prepareFixture("order-pickup");

    OrderResult pickedUp = orderStateUseCase.pickUp(
        CompleteOrderPickupCommand.of(fixture.order().getId(), fixture.store().getId()));
    Inquiry inquiry = inquiryJpaRepository.findById(fixture.inquiry().getId()).orElseThrow();

    assertEquals(OrderStatus.PICKED_UP, pickedUp.status());
    assertEquals(InquiryStatus.PICKED_UP, inquiry.getStatus());
  }

  @Test
  @DisplayName("이전 주문 픽업완료는 현재 문의 상태를 오염시키지 않는다")
  void doesNotUpdateInquiryStatusForOldSubmissionPickup() {
    Fixture fixture = prepareFixture("order-old-pickup");
    OrderFormSubmission currentSubmission =
        createSubmission(fixture.store(), fixture.buyer(), fixture.inquiry(), 8);
    Inquiry waitingInquiry =
        inquiryJpaRepository.findById(fixture.inquiry().getId()).orElseThrow();
    waitingInquiry.reopenSellerOnSubmission(currentSubmission.getId());
    inquiryJpaRepository.saveAndFlush(waitingInquiry);

    OrderResult pickedUp = orderStateUseCase.pickUp(
        CompleteOrderPickupCommand.of(fixture.order().getId(), fixture.store().getId()));
    Inquiry inquiry = inquiryJpaRepository.findById(fixture.inquiry().getId()).orElseThrow();

    assertEquals(OrderStatus.PICKED_UP, pickedUp.status());
    assertEquals(InquiryStatus.WAITING, inquiry.getStatus());
    assertEquals(currentSubmission.getId(), inquiry.getCurrentOrderFormSubmissionId());
  }

  @Test
  @DisplayName("허용되지 않은 주문 상태 전이는 차단한다")
  void rejectsInvalidTransitions() {
    Fixture fixture = prepareFixture("order-invalid");
    orderStateUseCase.pickUp(
        CompleteOrderPickupCommand.of(fixture.order().getId(), fixture.store().getId()));

    BaseException buyerException = assertThrows(
        BaseException.class,
        () -> orderStateUseCase.requestRefund(RequestOrderRefundCommand.of(
            fixture.order().getId(), fixture.buyer().getId(), "취소 요청")));
    BaseException sellerException = assertThrows(
        BaseException.class,
        () -> orderStateUseCase.refund(RefundOrderCommand.of(
            fixture.order().getId(), fixture.store().getId(), fixture.seller().getId(), "환불 처리")));

    assertEquals(OrderErrorCode.ORDER_STATUS_FORBIDDEN, buyerException.getErrorCode());
    assertEquals(OrderErrorCode.ORDER_STATUS_FORBIDDEN, sellerException.getErrorCode());
  }

  private Fixture prepareFixture(String prefix) {
    return prepareFixture(prefix, 7, List.of(new PolicyRule(0, 100)));
  }

  private OrderResult requestRefund(Fixture fixture) {
    return orderStateUseCase.requestRefund(RequestOrderRefundCommand.of(
        fixture.order().getId(), fixture.buyer().getId(), "취소 요청"));
  }

  private OrderDetailResult createManualRequiredRefund(Fixture fixture) {
    requestRefund(fixture);
    point3PaymentPort.refundResult = Point3RefundResult.manualRequired(
        null,
        "SETTLEMENT_DEADLINE_EXCEEDED",
        "정산 마감일이 지나 환불을 요청할 수 없습니다",
        "{\"paymentSessionId\":\"pymt_sess-test\"}");
    return orderStateUseCase.refund(RefundOrderCommand.of(
        fixture.order().getId(), fixture.store().getId(), fixture.seller().getId(), "판매자 환불"));
  }

  private long timelineCount(UUID inquiryId, ChatTimelineItemType type) {
    return chatTimelineItemJpaRepository.findAll().stream()
        .filter(item -> item.getInquiryId().equals(inquiryId))
        .filter(item -> item.getType() == type)
        .count();
  }

  private Fixture prepareFixture(
      String prefix, int pickupDaysFromToday, List<PolicyRule> refundPolicies) {
    User seller = saveUser(UserRole.SELLER, prefix + "-seller");
    User buyer = saveUser(UserRole.BUYER, prefix + "-buyer");
    Store store = storeJpaRepository.saveAndFlush(
        Store.create(seller.getId(), "주문 테스트 스토어 " + prefix, "order-test-" + UUID.randomUUID()));
    for (int index = 0; index < refundPolicies.size(); index++) {
      PolicyRule policy = refundPolicies.get(index);
      storeRefundPolicyJpaRepository.save(StoreRefundPolicy.create(
          store.getId(), policy.daysBeforePickup(), policy.refundRate(), index));
    }
    storeRefundPolicyJpaRepository.flush();
    Inquiry inquiry =
        inquiryJpaRepository.saveAndFlush(Inquiry.create(store.getId(), buyer.getId()));
    OrderFormSubmission submission = createSubmission(store, buyer, inquiry, pickupDaysFromToday);
    inquiry.reopenSellerOnSubmission(submission.getId());
    inquiryJpaRepository.saveAndFlush(inquiry);
    OrderConfirmation confirmation = orderConfirmationJpaRepository.saveAndFlush(
        createConfirmation(
            inquiry.getId(), submission.getId(), seller.getId(), pickupAt(pickupDaysFromToday)));
    PaymentAttempt paymentAttempt =
        paymentAttemptJpaRepository.saveAndFlush(createPaymentAttempt(confirmation, buyer));
    Order order = orderJpaRepository.saveAndFlush(Order.create(
        store.getId(),
        buyer.getId(),
        inquiry.getId(),
        confirmation.getId(),
        paymentAttempt.getId(),
        "P3-20260901-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
        confirmation.getMenuName(),
        confirmation.getOptionSummary(),
        paymentAttempt.getAmount(),
        confirmation.getPickupAt()));

    return new Fixture(seller, buyer, store, inquiry, confirmation, paymentAttempt, order);
  }

  private OrderConfirmation createConfirmation(
      UUID inquiryId, UUID submissionId, UUID sellerUserId, Instant pickupAt) {
    OrderConfirmation confirmation = OrderConfirmation.create(
        inquiryId,
        submissionId,
        sellerUserId,
        "초코 케이크 1호",
        "딸기 토핑",
        41000,
        pickupAt,
        "주문 테스트 스토어",
        "{\"answers\":[{\"label\":\"메뉴명\","
            + "\"selectedOptions\":[{\"label\":\"초코 케이크\",\"price\":0}]}]}",
        "[{\"label\":\"토핑\",\"value\":\"딸기\",\"amount\":3000}]",
        "픽업 전 연락");
    confirmation.sent(Instant.parse("2026-08-30T01:00:00Z"));
    confirmation.markPaid();
    return confirmation;
  }

  private OrderFormSubmission createSubmission(
      Store store, User buyer, Inquiry inquiry, int pickupDaysFromToday) {
    OrderFormTemplate template =
        orderFormTemplateJpaRepository.saveAndFlush(
            OrderFormTemplate.create(store.getId(), "주문서"));
    OrderFormSubmission submission = OrderFormSubmission.create(
        inquiry.getId(),
        template.getId(),
        buyer.getId(),
        LocalDate.now(KOREA_ZONE_ID).plus(pickupDaysFromToday, ChronoUnit.DAYS),
        LocalTime.NOON,
        "[{\"label\":\"메뉴명\",\"value\":\"초코 케이크\"}]",
        "[]",
        true);
    submission.markSellerViewed(Instant.parse("2026-08-30T00:00:00Z"));
    return orderFormSubmissionJpaRepository.saveAndFlush(submission);
  }

  private Instant pickupAt(int daysFromToday) {
    return LocalDate.now(KOREA_ZONE_ID)
        .plus(daysFromToday, ChronoUnit.DAYS)
        .atTime(LocalTime.NOON)
        .atZone(KOREA_ZONE_ID)
        .toInstant();
  }

  private Instant todayAtNoon() {
    return LocalDate.now(KOREA_ZONE_ID)
        .atTime(LocalTime.NOON)
        .atZone(KOREA_ZONE_ID)
        .toInstant();
  }

  private PaymentAttempt createPaymentAttempt(OrderConfirmation confirmation, User buyer) {
    PaymentAttempt paymentAttempt = PaymentAttempt.create(
        confirmation.getId(),
        buyer.getId(),
        "pymt_sess-" + UUID.randomUUID(),
        null,
        confirmation.getAmount(),
        Instant.parse("2026-09-01T01:00:00Z"));
    paymentAttempt.succeed("payer-" + UUID.randomUUID(), Instant.parse("2026-08-30T02:00:00Z"));
    return paymentAttempt;
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

  private long refundCompletedNotificationCount(UUID buyerUserId) {
    return notificationJpaRepository.findAllByUserIdOrderByCreatedAtDesc(buyerUserId).stream()
        .filter(notification -> notification.getType() == NotificationType.ORDER_REFUNDED)
        .count();
  }

  private long refundFailedNotificationCount(UUID buyerUserId) {
    return notificationJpaRepository.findAllByUserIdOrderByCreatedAtDesc(buyerUserId).stream()
        .filter(notification -> notification.getType() == NotificationType.ORDER_REFUND_FAILED)
        .count();
  }

  private record Fixture(
      User seller,
      User buyer,
      Store store,
      Inquiry inquiry,
      OrderConfirmation confirmation,
      PaymentAttempt paymentAttempt,
      Order order) {}

  private record PolicyRule(int daysBeforePickup, int refundRate) {}
}
