package io.point3.p3api.dashboard.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.point3.p3api.dashboard.application.result.SellerRevenueTransactionResult;
import io.point3.p3api.IntegrationTestSupport;
import io.point3.p3api.chat.domain.entity.ChatTimelineItem;
import io.point3.p3api.chat.infrastructure.persistence.ChatTimelineItemJpaRepository;
import io.point3.p3api.dashboard.application.result.SellerDashboardResult;
import io.point3.p3api.dashboard.application.result.SellerRevenueResult;
import io.point3.p3api.inquiry.domain.entity.Inquiry;
import io.point3.p3api.inquiry.infrastructure.persistence.InquiryJpaRepository;
import io.point3.p3api.order.domain.entity.Order;
import io.point3.p3api.order.domain.entity.OrderConfirmation;
import io.point3.p3api.order.domain.type.OrderStatus;
import io.point3.p3api.order.infrastructure.persistence.OrderConfirmationJpaRepository;
import io.point3.p3api.order.infrastructure.persistence.OrderJpaRepository;
import io.point3.p3api.payment.domain.entity.PaymentAttempt;
import io.point3.p3api.payment.domain.entity.Refund;
import io.point3.p3api.payment.domain.type.RefundOutcome;
import io.point3.p3api.payment.infrastructure.persistence.PaymentAttemptJpaRepository;
import io.point3.p3api.payment.infrastructure.persistence.RefundJpaRepository;
import io.point3.p3api.store.domain.entity.Store;
import io.point3.p3api.store.infrastructure.persistence.StoreJpaRepository;
import io.point3.p3api.user.domain.entity.User;
import io.point3.p3api.user.domain.type.SignupProvider;
import io.point3.p3api.user.domain.type.UserRole;
import io.point3.p3api.user.infrastructure.persistence.UserJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

class SellerDashboardServiceIntegrationTest extends IntegrationTestSupport {

  @Autowired
  private SellerDashboardQueryUseCase sellerDashboardQueryUseCase;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private StoreJpaRepository storeJpaRepository;

  @Autowired
  private InquiryJpaRepository inquiryJpaRepository;

  @Autowired
  private OrderConfirmationJpaRepository orderConfirmationJpaRepository;

  @Autowired
  private PaymentAttemptJpaRepository paymentAttemptJpaRepository;

  @Autowired
  private OrderJpaRepository orderJpaRepository;

  @Autowired
  private RefundJpaRepository refundJpaRepository;

  @Autowired
  private ChatTimelineItemJpaRepository chatTimelineItemJpaRepository;

  @Test
  @DisplayName("판매자 대시보드는 매출과 주문 현황을 스토어 범위로 조회한다")
  void getsDashboardSummary() {
    Fixture fixture = prepareFixture("dashboard-summary");
    Order earlyOrder = saveOrder(fixture, 100_000, "2026-08-10T01:00:00Z", "2026-08-24T15:30:00Z");
    Order cancelOrder = saveOrder(fixture, 50_000, "2026-08-20T01:00:00Z", "2026-08-25T01:00:00Z");
    Order weekOrder = saveOrder(fixture, 20_000, "2026-08-22T01:00:00Z", "2026-08-26T01:00:00Z");
    cancelOrder.requestRefund("구매자 취소 요청", Instant.parse("2026-08-25T02:00:00Z"));
    orderJpaRepository.saveAndFlush(cancelOrder);
    saveCompletedRefund(fixture, earlyOrder, 20_000, "2026-08-21T01:00:00Z");
    saveOrder(fixture, 70_000, "2026-07-31T14:59:59Z", "2026-08-27T01:00:00Z");
    saveOrder(
        prepareFixture("dashboard-other"), 90_000, "2026-08-20T01:00:00Z", "2026-08-25T01:00:00Z");
    chatTimelineItemJpaRepository.saveAndFlush(ChatTimelineItem.message(
        fixture.inquiry().getId(), fixture.buyer().getId(), UUID.randomUUID()));

    SellerDashboardResult result = sellerDashboardQueryUseCase.getSummary(
        SellerDashboardQueryCommand.of(fixture.store().getId(), fixture.seller().getId()));

    assertEquals(LocalDate.parse("2026-08-25"), result.today());
    assertEquals(LocalDate.parse("2026-08-24"), result.weekStartDate());
    assertEquals(LocalDate.parse("2026-08-30"), result.weekEndDate());
    assertEquals(170_000, result.currentMonthRevenue().paymentRevenueAmount());
    assertEquals(3, result.currentMonthRevenue().succeededPaymentCount());
    assertEquals(20_000, result.currentMonthRevenue().completedRefundAmount());
    assertEquals(1, result.currentMonthRevenue().completedRefundCount());
    assertEquals(150_000, result.currentMonthRevenue().netSalesAmount());
    assertEquals(450, result.currentMonthRevenue().settlementFeeAmount());
    assertEquals(149_550, result.currentMonthRevenue().settlementEstimateAmount());
    assertEquals(2, result.todayOrderCount());
    assertEquals(4, result.thisWeekOrderCount());
    assertEquals(3, result.paidOrderCount());
    assertEquals(1, result.cancelRefundRequestCount());
    assertEquals(1, result.unansweredInquiryCount());
    assertEquals(earlyOrder.getId(), result.todayOrders().get(0).orderId());
    assertEquals(cancelOrder.getId(), result.todayOrders().get(1).orderId());
    assertEquals(OrderStatus.PAID, weekOrder.getStatus());
  }

  @Test
  @DisplayName("기간별 매출은 성공 결제와 완료 환불 기준으로 계산한다")
  void getsRevenueByPeriod() {
    Fixture fixture = prepareFixture("dashboard-period");
    Order inPeriod = saveOrder(fixture, 80_000, "2026-08-15T01:00:00Z", "2026-08-29T01:00:00Z");
    saveOrder(fixture, 30_000, "2026-08-01T14:59:59Z", "2026-08-29T02:00:00Z");
    saveCompletedRefund(fixture, inPeriod, 10_000, "2026-08-18T01:00:00Z");

    SellerRevenueResult result =
        sellerDashboardQueryUseCase.getRevenue(SellerRevenueQueryCommand.of(
            fixture.store().getId(), LocalDate.parse("2026-08-02"), LocalDate.parse("2026-08-20")));

    assertEquals(LocalDate.parse("2026-08-02"), result.startDate());
    assertEquals(LocalDate.parse("2026-08-20"), result.endDate());
    assertEquals(80_000, result.paymentRevenueAmount());
    assertEquals(1, result.succeededPaymentCount());
    assertEquals(10_000, result.completedRefundAmount());
    assertEquals(1, result.completedRefundCount());
    assertEquals(70_000, result.netSalesAmount());
    assertEquals(30, result.settlementFeeRateBasisPoints());
    assertEquals(210, result.settlementFeeAmount());
    assertEquals(69_790, result.settlementEstimateAmount());
  }

  @Test
  @DisplayName("매출 거래 목록은 결제와 완료 환불 거래를 분리해 조회한다")
  void getsRevenueTransactions() {
    Fixture fixture = prepareFixture("dashboard-transactions");
    Order previousPaymentOrder =
        saveOrder(fixture, 100_000, "2026-07-31T14:59:59Z", "2026-08-29T01:00:00Z");
    Refund currentRefund =
        saveCompletedRefund(fixture, previousPaymentOrder, 20_000, "2026-08-02T00:00:00Z");
    Order laterRefundOrder =
        saveOrder(fixture, 80_000, "2026-08-02T00:00:00Z", "2026-08-29T02:00:00Z");
    Refund laterRefund =
        saveCompletedRefund(fixture, laterRefundOrder, 10_000, "2026-08-21T00:00:00Z");
    Order partialRefundOrder =
        saveOrder(fixture, 60_000, "2026-08-03T00:00:00Z", "2026-08-29T03:00:00Z");
    Refund partialRefund =
        saveCompletedRefund(fixture, partialRefundOrder, 15_000, "2026-08-04T00:00:00Z");
    Order manualRefundOrder =
        saveOrder(fixture, 40_000, "2026-08-05T00:00:00Z", "2026-08-29T04:00:00Z");
    Refund manualRefund =
        saveManualRefund(fixture, manualRefundOrder, 5_000, "2026-08-06T00:00:00Z");
    Refund failedRefund =
        saveFailedRefund(fixture, partialRefundOrder, 3_000, "2026-08-07T00:00:00Z");
    saveOrder(fixture, 30_000, "2026-08-20T15:00:00Z", "2026-08-29T05:00:00Z");

    Fixture otherFixture = prepareFixture("dashboard-transactions-other");
    Order otherOrder =
        saveOrder(otherFixture, 90_000, "2026-08-03T00:00:00Z", "2026-08-29T06:00:00Z");
    saveCompletedRefund(otherFixture, otherOrder, 7_000, "2026-08-04T00:00:00Z");

    List<SellerRevenueTransactionResult> payments =
        sellerDashboardQueryUseCase.getRevenueTransactions(SellerRevenueTransactionQueryCommand.of(
            fixture.store().getId(),
            SellerRevenueTransactionType.PAYMENT,
            LocalDate.parse("2026-08-02"),
            LocalDate.parse("2026-08-20")));
    List<SellerRevenueTransactionResult> refunds =
        sellerDashboardQueryUseCase.getRevenueTransactions(SellerRevenueTransactionQueryCommand.of(
            fixture.store().getId(),
            SellerRevenueTransactionType.REFUND,
            LocalDate.parse("2026-08-02"),
            LocalDate.parse("2026-08-20")));

    assertEquals(3, payments.size());
    SellerRevenueTransactionResult payment = findByTransactionId(
        payments, laterRefundOrder.getPaymentAttemptId());
    assertEquals(SellerRevenueTransactionType.PAYMENT, payment.transactionType());
    assertEquals(laterRefundOrder.getId(), payment.orderId());
    assertEquals(80_000, payment.amount());
    assertEquals(Instant.parse("2026-08-02T00:00:00Z"), payment.occurredAt());
    assertNull(payment.refundId());
    assertEquals(fixture.buyer().getName(), payment.buyerName());
    assertFalse(hasTransactionId(payments, previousPaymentOrder.getPaymentAttemptId()));
    assertFalse(hasTransactionId(payments, otherOrder.getPaymentAttemptId()));

    assertEquals(3, refunds.size());
    SellerRevenueTransactionResult refund = findByTransactionId(refunds, currentRefund.getId());
    assertEquals(SellerRevenueTransactionType.REFUND, refund.transactionType());
    assertEquals(previousPaymentOrder.getId(), refund.orderId());
    assertEquals(previousPaymentOrder.getPaymentAttemptId(), refund.paymentAttemptId());
    assertEquals(currentRefund.getId(), refund.refundId());
    assertEquals(20_000, refund.amount());
    assertEquals(Instant.parse("2026-08-02T00:00:00Z"), refund.occurredAt());
    assertEquals(fixture.buyer().getName(), refund.buyerName());
    assertEquals(15_000, findByTransactionId(refunds, partialRefund.getId()).amount());
    assertEquals(5_000, findByTransactionId(refunds, manualRefund.getId()).amount());
    assertFalse(hasTransactionId(refunds, laterRefund.getId()));
    assertFalse(hasTransactionId(refunds, failedRefund.getId()));
  }

  private Fixture prepareFixture(String prefix) {
    User seller = saveUser(UserRole.SELLER, prefix + "-seller");
    User buyer = saveUser(UserRole.BUYER, prefix + "-buyer");
    Store store = storeJpaRepository.saveAndFlush(Store.create(
        seller.getId(), "대시보드 테스트 스토어 " + prefix, "dashboard-test-" + UUID.randomUUID()));
    Inquiry inquiry =
        inquiryJpaRepository.saveAndFlush(Inquiry.create(store.getId(), buyer.getId()));

    return new Fixture(seller, buyer, store, inquiry);
  }

  private Order saveOrder(
      Fixture fixture, long amount, String paymentCompletedAt, String pickupAt) {
    OrderConfirmation confirmation = orderConfirmationJpaRepository.saveAndFlush(
        createConfirmation(fixture.inquiry().getId(), fixture.seller().getId(), amount, pickupAt));
    PaymentAttempt paymentAttempt = paymentAttemptJpaRepository.saveAndFlush(
        createPaymentAttempt(confirmation, fixture.buyer(), paymentCompletedAt));

    return orderJpaRepository.saveAndFlush(Order.create(
        fixture.store().getId(),
        fixture.buyer().getId(),
        fixture.inquiry().getId(),
        confirmation.getId(),
        paymentAttempt.getId(),
        "P3-20260825-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
        confirmation.getMenuName(),
        confirmation.getOptionSummary(),
        paymentAttempt.getAmount(),
        confirmation.getPickupAt()));
  }

  private Refund saveCompletedRefund(Fixture fixture, Order order, long amount, String completedAt) {
    Refund refund = Refund.create(
        order.getId(), order.getPaymentAttemptId(), fixture.seller().getId(), amount, "환불 완료");
    refund.complete(Instant.parse(completedAt));
    return refundJpaRepository.saveAndFlush(refund);
  }

  private Refund saveManualRefund(Fixture fixture, Order order, long amount, String completedAt) {
    Refund refund = Refund.create(
        order.getId(), order.getPaymentAttemptId(), fixture.seller().getId(), amount, "수동 환불");
    refund.startProcessing();
    refund.fail(
        RefundOutcome.MANUAL_REQUIRED,
        null,
        "SETTLED",
        "정산 완료",
        null,
        Instant.parse("2026-08-05T00:00:00Z"));
    refund.completeManually(fixture.seller().getId(), Instant.parse(completedAt));
    return refundJpaRepository.saveAndFlush(refund);
  }

  private Refund saveFailedRefund(Fixture fixture, Order order, long amount, String failedAt) {
    Refund refund = Refund.create(
        order.getId(), order.getPaymentAttemptId(), fixture.seller().getId(), amount, "환불 실패");
    refund.startProcessing();
    refund.fail(
        RefundOutcome.RETRYABLE,
        null,
        "TEMPORARY_FAILURE",
        "일시 실패",
        null,
        Instant.parse(failedAt));
    return refundJpaRepository.saveAndFlush(refund);
  }

  private OrderConfirmation createConfirmation(
      UUID inquiryId, UUID sellerUserId, long amount, String pickupAt) {
    OrderConfirmation confirmation = OrderConfirmation.create(
        inquiryId,
        null,
        sellerUserId,
        "초코 케이크",
        "딸기 토핑",
        amount,
        Instant.parse(pickupAt),
        "대시보드 테스트 스토어",
        null,
        null,
        null);
    confirmation.sent(Instant.parse("2026-08-01T01:00:00Z"));
    confirmation.markPaid();
    return confirmation;
  }

  private PaymentAttempt createPaymentAttempt(
      OrderConfirmation confirmation, User buyer, String completedAt) {
    PaymentAttempt paymentAttempt = PaymentAttempt.create(
        confirmation.getId(),
        buyer.getId(),
        "pymt_sess-" + UUID.randomUUID(),
        null,
        confirmation.getAmount(),
        Instant.parse("2026-08-30T01:00:00Z"));
    paymentAttempt.succeed("payer-" + UUID.randomUUID(), Instant.parse(completedAt));
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

  private SellerRevenueTransactionResult findByTransactionId(
      List<SellerRevenueTransactionResult> results, UUID transactionId) {
    return results.stream()
        .filter(result -> result.transactionId().equals(transactionId))
        .findFirst()
        .orElseThrow();
  }

  private boolean hasTransactionId(
      List<SellerRevenueTransactionResult> results, UUID transactionId) {
    return results.stream().anyMatch(result -> result.transactionId().equals(transactionId));
  }

  private record Fixture(User seller, User buyer, Store store, Inquiry inquiry) {}

  @TestConfiguration
  static class DashboardClockTestConfiguration {

    @Bean
    @Primary
    Clock fixedDashboardClock() {
      return Clock.fixed(Instant.parse("2026-08-25T02:00:00Z"), ZoneOffset.UTC);
    }
  }
}
