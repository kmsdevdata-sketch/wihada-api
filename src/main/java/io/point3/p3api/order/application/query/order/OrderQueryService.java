package io.point3.p3api.order.application.query.order;

import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.CommonErrorCode;
import io.point3.p3api.exception.code.OrderConfirmationErrorCode;
import io.point3.p3api.exception.code.OrderErrorCode;
import io.point3.p3api.exception.code.PaymentErrorCode;
import io.point3.p3api.order.application.option.OrderOptionRowResolver;
import io.point3.p3api.order.application.port.OrderConfirmationPersistencePort;
import io.point3.p3api.order.application.port.OrderPersistencePort;
import io.point3.p3api.order.application.refund.OrderRefundCalculationResolver;
import io.point3.p3api.order.application.result.OrderDetailResult;
import io.point3.p3api.order.application.result.OrderRefundQuoteResult;
import io.point3.p3api.order.application.result.OrderResult;
import io.point3.p3api.order.domain.entity.Order;
import io.point3.p3api.payment.application.port.PaymentAttemptPersistencePort;
import io.point3.p3api.payment.application.port.RefundPersistencePort;
import io.point3.p3api.payment.application.result.PaymentAttemptResult;
import io.point3.p3api.payment.application.result.RefundResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderQueryService implements OrderQueryUseCase {
  private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

  private final OrderPersistencePort orderPersistencePort;
  private final OrderConfirmationPersistencePort orderConfirmationPersistencePort;
  private final OrderOptionRowResolver orderOptionRowResolver;
  private final PaymentAttemptPersistencePort paymentAttemptPersistencePort;
  private final RefundPersistencePort refundPersistencePort;
  private final OrderReferenceAssetDeliveryService orderReferenceAssetDeliveryService;
  private final OrderRefundCalculationResolver orderRefundCalculationResolver;
  private final Clock clock;

  @Override
  public List<OrderResult> getBuyerOrders(UUID buyerUserId) {
    List<Order> orders = orderPersistencePort.findAllByBuyerUserId(buyerUserId);
    var referenceAssetsByOrderId =
        orderReferenceAssetDeliveryService.appendDeliveriesByOrderId(orders);
    return orders.stream()
        .map(order -> OrderResult.from(
            order, referenceAssetsByOrderId.getOrDefault(order.getId(), List.of())))
        .toList();
  }

  @Override
  public OrderDetailResult getBuyerOrder(UUID orderId, UUID buyerUserId) {
    return toDetail(orderPersistencePort
        .findByIdAndBuyerUserId(orderId, buyerUserId)
        .orElseThrow(() -> new BaseException(OrderErrorCode.ORDER_NOT_FOUND)));
  }

  @Override
  public List<OrderResult> getSellerOrders(UUID storeId) {
    return getSellerOrders(SellerOrderListQuery.of(storeId, null, null, null, null));
  }

  @Override
  public List<OrderResult> getSellerOrders(SellerOrderListQuery query) {
    validateDateRange(query.startDate(), query.endDate());
    Instant startInclusive = startInclusive(query.startDate());
    Instant endExclusive = endExclusive(query.endDate());

    List<Order> orders = orderPersistencePort.findSellerOrders(query, startInclusive, endExclusive);
    var referenceAssetsByOrderId =
        orderReferenceAssetDeliveryService.appendDeliveriesByOrderId(orders);
    return orders.stream()
        .map(order -> OrderResult.from(
            order, referenceAssetsByOrderId.getOrDefault(order.getId(), List.of())))
        .toList();
  }

  @Override
  public OrderDetailResult getSellerOrder(UUID orderId, UUID storeId) {
    return toDetail(orderPersistencePort
        .findByIdAndStoreId(orderId, storeId)
        .orElseThrow(() -> new BaseException(OrderErrorCode.ORDER_NOT_FOUND)));
  }

  @Override
  public OrderRefundQuoteResult getSellerOrderRefundQuote(UUID orderId, UUID storeId) {
    Order order = orderPersistencePort
        .findByIdAndStoreId(orderId, storeId)
        .orElseThrow(() -> new BaseException(OrderErrorCode.ORDER_NOT_FOUND));
    return OrderRefundQuoteResult.from(
        order.getId(), orderRefundCalculationResolver.resolve(order, Instant.now(clock)));
  }

  private OrderDetailResult toDetail(Order order) {
    Instant now = Instant.now(clock);

    PaymentAttemptResult paymentAttempt = paymentAttemptPersistencePort
        .findById(order.getPaymentAttemptId())
        .map(attempt -> PaymentAttemptResult.from(attempt, now))
        .orElseThrow(() -> new BaseException(PaymentErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));

    List<RefundResult> refunds = refundPersistencePort.findAllByOrderId(order.getId()).stream()
        .map(RefundResult::from)
        .toList();
    var confirmation = orderConfirmationPersistencePort
        .findById(order.getConfirmationId())
        .orElseThrow(
            () -> new BaseException(OrderConfirmationErrorCode.ORDER_CONFIRMATION_NOT_FOUND));

    return OrderDetailResult.of(
        OrderResult.from(
            order,
            orderReferenceAssetDeliveryService.appendDeliveries(order.getStartReferenceAssets())),
        paymentAttempt,
        refunds,
        orderOptionRowResolver.fromConfirmation(confirmation));
  }

  private void validateDateRange(LocalDate startDate, LocalDate endDate) {
    if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
      throw new BaseException(CommonErrorCode.INVALID_INPUT, "startDate must not be after endDate");
    }
  }

  private Instant startInclusive(LocalDate startDate) {
    if (startDate == null) {
      return null;
    }
    return startDate.atStartOfDay(KOREA_ZONE_ID).toInstant();
  }

  private Instant endExclusive(LocalDate endDate) {
    if (endDate == null) {
      return null;
    }
    return endDate.plusDays(1).atStartOfDay(KOREA_ZONE_ID).toInstant();
  }
}
