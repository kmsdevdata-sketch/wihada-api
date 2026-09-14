package io.point3.p3api.dashboard.application.query;

import io.point3.p3api.chat.application.port.ChatTimelineItemPort;
import io.point3.p3api.dashboard.application.result.SellerDashboardResult;
import io.point3.p3api.dashboard.application.result.SellerRevenueResult;
import io.point3.p3api.dashboard.application.result.SellerRevenueTransactionResult;
import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.CommonErrorCode;
import io.point3.p3api.inquiry.application.port.InquiryPersistencePort;
import io.point3.p3api.inquiry.domain.entity.Inquiry;
import io.point3.p3api.order.application.port.OrderPersistencePort;
import io.point3.p3api.order.application.query.order.OrderReferenceAssetDeliveryService;
import io.point3.p3api.order.application.result.OrderCalendarOrderResult;
import io.point3.p3api.order.application.result.OrderReferenceAssetResult;
import io.point3.p3api.order.domain.entity.Order;
import io.point3.p3api.order.domain.type.OrderStatus;
import io.point3.p3api.payment.application.port.PaymentAttemptPersistencePort;
import io.point3.p3api.payment.application.port.RefundPersistencePort;
import io.point3.p3api.payment.domain.entity.PaymentAttempt;
import io.point3.p3api.payment.domain.entity.Refund;
import io.point3.p3api.user.application.port.UserPersistencePort;
import io.point3.p3api.user.domain.entity.User;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SellerDashboardService implements SellerDashboardQueryUseCase {

  private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
  private static final int FEE_RATE_BASIS_POINTS = 30;

  private final OrderPersistencePort orderPersistencePort;
  private final PaymentAttemptPersistencePort paymentAttemptPersistencePort;
  private final RefundPersistencePort refundPersistencePort;
  private final InquiryPersistencePort inquiryPersistencePort;
  private final ChatTimelineItemPort chatTimelineItemPort;
  private final UserPersistencePort userPersistencePort;
  private final OrderReferenceAssetDeliveryService orderReferenceAssetDeliveryService;
  private final Clock clock;

  @Override
  public SellerDashboardResult getSummary(SellerDashboardQueryCommand command) {
    LocalDate today = LocalDate.now(clock.withZone(KOREA_ZONE));
    LocalDate tomorrow = today.plusDays(1);
    LocalDate weekStartDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate weekEndExclusive = weekStartDate.plusWeeks(1);
    YearMonth currentMonth = YearMonth.from(today);

    List<Order> todayOrders = orderPersistencePort.findCalendarOrders(
        command.storeId(), toInstant(today), toInstant(tomorrow));
    List<Order> weekOrders = orderPersistencePort.findCalendarOrders(
        command.storeId(), toInstant(weekStartDate), toInstant(weekEndExclusive));

    return new SellerDashboardResult(
        today,
        weekStartDate,
        weekEndExclusive.minusDays(1),
        getRevenue(command.storeId(), currentMonth.atDay(1), currentMonth.atEndOfMonth()),
        todayOrders.size(),
        weekOrders.size(),
        orderPersistencePort.countByStoreIdAndStatus(command.storeId(), OrderStatus.PAID),
        orderPersistencePort.countByStoreIdAndStatuses(
            command.storeId(), List.of(OrderStatus.REFUND_REQUESTED)),
        countUnanswered(command),
        todayOrders.stream()
            .map(order -> OrderCalendarOrderResult.from(order, KOREA_ZONE))
            .toList());
  }

  @Override
  public SellerRevenueResult getRevenue(SellerRevenueQueryCommand command) {
    return getRevenue(command.storeId(), command.startDate(), command.endDate());
  }

  private SellerRevenueResult getRevenue(UUID storeId, LocalDate startDate, LocalDate endDate) {
    Instant startInclusive = toInstant(startDate);
    Instant endExclusive = toInstant(endDate.plusDays(1));
    long paymentRevenueAmount =
        orderPersistencePort.sumSucceededPaymentAmount(storeId, startInclusive, endExclusive);
    long succeededPaymentCount =
        orderPersistencePort.countSucceededPayments(storeId, startInclusive, endExclusive);
    long completedRefundAmount =
        refundPersistencePort.sumCompletedAmount(storeId, startInclusive, endExclusive);
    long completedRefundCount =
        refundPersistencePort.countCompleted(storeId, startInclusive, endExclusive);

    return SellerRevenueResult.of(
        startDate,
        endDate,
        paymentRevenueAmount,
        succeededPaymentCount,
        completedRefundAmount,
        completedRefundCount,
        FEE_RATE_BASIS_POINTS);
  }

  @Override
  public List<SellerRevenueTransactionResult> getRevenueTransactions(
      SellerRevenueTransactionQueryCommand command) {
    Instant startInclusive = toInstant(command.startDate());
    Instant endExclusive = toInstant(command.endDate().plusDays(1));

    if (command.type() == SellerRevenueTransactionType.PAYMENT) {
      return getPaymentTransactions(command.storeId(), startInclusive, endExclusive);
    }

    return getRefundTransactions(command.storeId(), startInclusive, endExclusive);
  }

  private long countUnanswered(SellerDashboardQueryCommand command) {
    return inquiryPersistencePort.findAllByStoreId(command.storeId()).stream()
        .filter(inquiry -> hasUnreadBuyerMessage(inquiry, command.sellerUserId()))
        .count();
  }

  private boolean hasUnreadBuyerMessage(Inquiry inquiry, UUID sellerUserId) {
    return chatTimelineItemPort.countUnread(
            inquiry.getId(), sellerUserId, inquiry.getSellerLastReadAt())
        > 0;
  }

  private Instant toInstant(LocalDate date) {
    return date.atStartOfDay(KOREA_ZONE).toInstant();
  }

  private List<SellerRevenueTransactionResult> getPaymentTransactions(
      UUID storeId, Instant startInclusive, Instant endExclusive) {
    List<Order> orders =
        orderPersistencePort.findSucceededPaymentOrders(storeId, startInclusive, endExclusive);
    Map<UUID, PaymentAttempt> payments = collectPayments(orders);
    Map<UUID, User> buyers = collectBuyers(orders);
    Map<UUID, List<OrderReferenceAssetResult>> assets =
        orderReferenceAssetDeliveryService.appendDeliveriesByOrderId(orders);

    return orders.stream()
        .map(
            order -> toPaymentRow(order, payments.get(order.getPaymentAttemptId()), buyers, assets))
        .toList();
  }

  private List<SellerRevenueTransactionResult> getRefundTransactions(
      UUID storeId, Instant startInclusive, Instant endExclusive) {
    List<Refund> refunds =
        refundPersistencePort.findCompletedByStoreId(storeId, startInclusive, endExclusive);
    List<UUID> orderIds = refunds.stream().map(Refund::getOrderId).distinct().toList();
    Map<UUID, Order> orders = orderPersistencePort.findAllByIds(orderIds).stream()
        .collect(Collectors.toMap(Order::getId, Function.identity()));
    List<Order> orderList = orders.values().stream().toList();
    Map<UUID, User> buyers = collectBuyers(orderList);
    Map<UUID, List<OrderReferenceAssetResult>> assets =
        orderReferenceAssetDeliveryService.appendDeliveriesByOrderId(orderList);

    return refunds.stream()
        .map(refund -> toRefundRow(refund, orders.get(refund.getOrderId()), buyers, assets))
        .toList();
  }

  private Map<UUID, PaymentAttempt> collectPayments(List<Order> orders) {
    List<UUID> paymentAttemptIds =
        orders.stream().map(Order::getPaymentAttemptId).distinct().toList();
    if (paymentAttemptIds.isEmpty()) {
      return Map.of();
    }
    return paymentAttemptPersistencePort.findAllById(paymentAttemptIds).stream()
        .collect(Collectors.toMap(PaymentAttempt::getId, Function.identity()));
  }

  private Map<UUID, User> collectBuyers(List<Order> orders) {
    List<UUID> buyerUserIds =
        orders.stream().map(Order::getBuyerUserId).distinct().toList();
    if (buyerUserIds.isEmpty()) {
      return Map.of();
    }
    return userPersistencePort.findAllById(buyerUserIds).stream()
        .collect(Collectors.toMap(User::getId, Function.identity()));
  }

  private SellerRevenueTransactionResult toPaymentRow(
      Order order,
      PaymentAttempt payment,
      Map<UUID, User> buyers,
      Map<UUID, List<OrderReferenceAssetResult>> assets) {
    if (payment == null || payment.getCompletedAt() == null) {
      throw new BaseException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    return new SellerRevenueTransactionResult(
        SellerRevenueTransactionType.PAYMENT,
        payment.getId(),
        order.getId(),
        payment.getId(),
        null,
        order.getInquiryId(),
        payment.getAmount(),
        payment.getCompletedAt(),
        order.getPickupAt(),
        buyerName(order, buyers),
        assets.getOrDefault(order.getId(), List.of()));
  }

  private SellerRevenueTransactionResult toRefundRow(
      Refund refund,
      Order order,
      Map<UUID, User> buyers,
      Map<UUID, List<OrderReferenceAssetResult>> assets) {
    if (order == null || refund.getCompletedAt() == null) {
      throw new BaseException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    return new SellerRevenueTransactionResult(
        SellerRevenueTransactionType.REFUND,
        refund.getId(),
        order.getId(),
        refund.getPaymentAttemptId(),
        refund.getId(),
        order.getInquiryId(),
        refund.getAmount(),
        refund.getCompletedAt(),
        order.getPickupAt(),
        buyerName(order, buyers),
        assets.getOrDefault(order.getId(), List.of()));
  }

  private String buyerName(Order order, Map<UUID, User> buyers) {
    User buyer = buyers.get(order.getBuyerUserId());
    if (buyer == null) {
      return null;
    }
    return buyer.getName();
  }
}
