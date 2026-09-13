package io.point3.p3api.order.application.state;

import io.point3.p3api.chat.application.timeline.ChatTimelineItemPublisher;
import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.OrderConfirmationErrorCode;
import io.point3.p3api.exception.code.OrderErrorCode;
import io.point3.p3api.exception.code.PaymentErrorCode;
import io.point3.p3api.inquiry.application.port.InquiryPersistencePort;
import io.point3.p3api.inquiry.application.realtime.InquiryListChangeEventPublisher;
import io.point3.p3api.inquiry.domain.entity.Inquiry;
import io.point3.p3api.notification.application.create.CreateNotificationCommand;
import io.point3.p3api.notification.application.create.NotificationCreateUseCase;
import io.point3.p3api.notification.domain.type.NotificationReferenceType;
import io.point3.p3api.notification.domain.type.NotificationType;
import io.point3.p3api.order.application.option.OrderOptionRowResolver;
import io.point3.p3api.order.application.port.OrderConfirmationPersistencePort;
import io.point3.p3api.order.application.port.OrderPersistencePort;
import io.point3.p3api.order.application.port.OrderStatusHistoryPersistencePort;
import io.point3.p3api.order.application.query.order.OrderReferenceAssetDeliveryService;
import io.point3.p3api.order.application.refund.OrderRefundCalculation;
import io.point3.p3api.order.application.refund.OrderRefundCalculationResolver;
import io.point3.p3api.order.application.result.OrderDetailResult;
import io.point3.p3api.order.application.result.OrderResult;
import io.point3.p3api.order.domain.entity.Order;
import io.point3.p3api.order.domain.entity.OrderStatusHistory;
import io.point3.p3api.order.domain.type.OrderStatus;
import io.point3.p3api.payment.application.port.PaymentAttemptPersistencePort;
import io.point3.p3api.payment.application.port.Point3PaymentException;
import io.point3.p3api.payment.application.port.Point3PaymentPort;
import io.point3.p3api.payment.application.port.Point3RefundResult;
import io.point3.p3api.payment.application.port.Point3RefundStatusResult;
import io.point3.p3api.payment.application.port.RefundPersistencePort;
import io.point3.p3api.payment.application.result.PaymentAttemptResult;
import io.point3.p3api.payment.application.result.RefundResult;
import io.point3.p3api.payment.domain.entity.PaymentAttempt;
import io.point3.p3api.payment.domain.entity.Refund;
import io.point3.p3api.payment.domain.type.RefundOutcome;
import io.point3.p3api.payment.domain.type.RefundStatus;
import io.point3.p3api.store.application.port.StorePersistencePort;
import io.point3.p3api.store.domain.entity.Store;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderStateService implements OrderStateUseCase {

  private final OrderPersistencePort orderPersistencePort;
  private final OrderConfirmationPersistencePort orderConfirmationPersistencePort;
  private final OrderStatusHistoryPersistencePort orderStatusHistoryPersistencePort;
  private final OrderOptionRowResolver orderOptionRowResolver;
  private final InquiryPersistencePort inquiryPersistencePort;
  private final PaymentAttemptPersistencePort paymentAttemptPersistencePort;
  private final RefundPersistencePort refundPersistencePort;
  private final Point3PaymentPort point3PaymentPort;
  private final OrderRefundCalculationResolver orderRefundCalculationResolver;
  private final Clock clock;
  private final StorePersistencePort storePersistencePort;
  private final NotificationCreateUseCase notificationCreateUseCase;
  private final InquiryListChangeEventPublisher inquiryListChangeEventPublisher;
  private final OrderReferenceAssetDeliveryService orderReferenceAssetDeliveryService;
  private final ChatTimelineItemPublisher chatTimelineItemPublisher;

  @Override
  public OrderResult pickUp(CompleteOrderPickupCommand command) {
    Order order = getSellerOrder(command.orderId(), command.storeId());
    changeStatus(order, null, "PICKUP_COMPLETED", order::markPickedUp);

    Inquiry inquiry = inquiryPersistencePort
        .findById(order.getInquiryId())
        .orElseThrow(() -> new BaseException(OrderErrorCode.ORDER_NOT_FOUND));
    inquiry.markPickedUp();
    inquiryListChangeEventPublisher.publishInquiryChanged(inquiry.getId());

    return toResult(order);
  }

  @Override
  public OrderResult requestRefund(RequestOrderRefundCommand command) {
    Order order = orderPersistencePort
        .findByIdAndBuyerUserId(command.orderId(), command.buyerUserId())
        .orElseThrow(() -> new BaseException(OrderErrorCode.ORDER_NOT_FOUND));

    changeStatus(
        order,
        command.buyerUserId(),
        command.reason(),
        () -> order.requestRefund(command.reason(), Instant.now(clock)));
    notifySellerRefundRequested(order);
    chatTimelineItemPublisher.publishOrderRefundRequested(
        order.getInquiryId(), command.buyerUserId(), order.getId());

    return toResult(order);
  }

  @Override
  public OrderDetailResult refund(RefundOrderCommand command) {
    Order order = getSellerOrderForUpdate(command.orderId(), command.storeId());
    OrderDetailResult existingResult = handleExistingRefund(order, command.sellerUserId());
    if (existingResult != null) {
      return existingResult;
    }
    validateRefundable(order);
    Instant processingAt = Instant.now(clock);
    OrderRefundCalculation calculation =
        orderRefundCalculationResolver.resolve(order, processingAt);
    Refund refund = Refund.create(
        order.getId(),
        order.getPaymentAttemptId(),
        command.sellerUserId(),
        calculation.refundAmount(),
        calculation.refundRate(),
        command.reason());
    refund.startProcessing();
    refund = refundPersistencePort.save(refund);
    PaymentAttempt paymentAttempt = paymentAttemptPersistencePort
        .findById(order.getPaymentAttemptId())
        .orElseThrow(() -> new BaseException(PaymentErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));
    if (refund.getAmount() == 0) {
      completeRefund(order, refund, command, processingAt);
      return toDetail(order);
    }
    Point3RefundResult result = requestPoint3Refund(paymentAttempt, refund, command.reason());
    applyRefundResult(
        order, refund, command.sellerUserId(), command.reason(), result, processingAt);
    refundPersistencePort.save(refund);

    return toDetail(order);
  }

  @Override
  public OrderDetailResult refreshRefund(RefreshOrderRefundCommand command) {
    Order order = getSellerOrderForUpdate(command.orderId(), command.storeId());
    Refund refund = refundPersistencePort.findAllByOrderId(order.getId()).stream()
        .filter(item -> item.getStatus() == RefundStatus.PROCESSING)
        .findFirst()
        .orElse(null);
    if (refund == null) {
      return toDetail(order);
    }
    PaymentAttempt paymentAttempt = paymentAttemptPersistencePort
        .findById(order.getPaymentAttemptId())
        .orElseThrow(() -> new BaseException(PaymentErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));
    Point3RefundResult result = refreshPoint3Refund(paymentAttempt, refund);
    applyRefundResult(
        order, refund, command.sellerUserId(), refund.getReason(), result, Instant.now(clock));
    refundPersistencePort.save(refund);

    return toDetail(order);
  }

  @Override
  public OrderDetailResult completeManualRefund(CompleteManualOrderRefundCommand command) {
    Order order = getSellerOrderForUpdate(command.orderId(), command.storeId());
    Refund refund = refundPersistencePort
        .findById(command.refundId())
        .orElseThrow(() -> new BaseException(OrderErrorCode.ORDER_NOT_FOUND));
    validateManualRefundTarget(order, refund);
    if (order.getStatus() == OrderStatus.REFUNDED && refund.isManualCompleted()) {
      return toDetail(order);
    }

    ensureNoCompletedRefund(order, refund.getId());
    Instant completedAt = Instant.now(clock);
    changeStatus(
        order,
        command.sellerUserId(),
        "MANUAL_REFUND_COMPLETED",
        completedAt,
        () -> order.refund(refund.getReason()));
    refund.completeManually(command.sellerUserId(), completedAt);
    notifyBuyerRefundCompleted(order);
    chatTimelineItemPublisher.publishOrderRefundCompleted(
        order.getInquiryId(), command.sellerUserId(), order.getId());
    refundPersistencePort.save(refund);

    return toDetail(order);
  }

  private void completeRefund(
      Order order, Refund refund, RefundOrderCommand command, Instant completedAt) {
    changeStatus(
        order,
        command.sellerUserId(),
        command.reason(),
        completedAt,
        () -> order.refund(command.reason()));
    refund.completeZeroAmount(command.sellerUserId(), completedAt);
    notifyBuyerRefundCompleted(order);
    chatTimelineItemPublisher.publishOrderRefundCompleted(
        order.getInquiryId(), command.sellerUserId(), order.getId());
  }

  private void completeRefund(
      Order order,
      Refund refund,
      UUID changedBy,
      String reason,
      String providerRefundId,
      Instant completedAt) {
    changeStatus(order, changedBy, reason, completedAt, () -> order.refund(reason));
    refund.completeAutomatically(providerRefundId, changedBy, completedAt);
    notifyBuyerRefundCompleted(order);
    chatTimelineItemPublisher.publishOrderRefundCompleted(
        order.getInquiryId(), changedBy, order.getId());
  }

  private void validateManualRefundTarget(Order order, Refund refund) {
    if (!refund.getOrderId().equals(order.getId())) {
      throw new BaseException(OrderErrorCode.ORDER_NOT_FOUND);
    }
    if (order.getStatus() == OrderStatus.REFUNDED && refund.isManualCompleted()) {
      return;
    }
    if ((order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.REFUND_REQUESTED)
        || refund.getStatus() != RefundStatus.FAILED
        || refund.getOutcome() != RefundOutcome.MANUAL_REQUIRED) {
      throw new BaseException(OrderErrorCode.ORDER_STATUS_FORBIDDEN);
    }
  }

  private void ensureNoCompletedRefund(Order order, UUID targetRefundId) {
    boolean completedExists = refundPersistencePort.findAllByOrderId(order.getId()).stream()
        .anyMatch(refund ->
            !refund.getId().equals(targetRefundId) && refund.getStatus() == RefundStatus.COMPLETED);
    if (completedExists) {
      throw new BaseException(OrderErrorCode.ORDER_REFUND_ALREADY_PROCESSED);
    }
  }

  private void validateRefundable(Order order) {
    try {
      order.validateRefundable();
    } catch (IllegalStateException exception) {
      throw new BaseException(OrderErrorCode.ORDER_STATUS_FORBIDDEN);
    }
  }

  private OrderDetailResult handleExistingRefund(Order order, UUID sellerUserId) {
    List<Refund> refunds = refundPersistencePort.findAllByOrderId(order.getId());
    Refund latest = refunds.stream().findFirst().orElse(null);
    if (latest == null || latest.isRetryableFailure()) {
      return null;
    }
    if (latest.getStatus() == RefundStatus.PROCESSING) {
      PaymentAttempt paymentAttempt = paymentAttemptPersistencePort
          .findById(order.getPaymentAttemptId())
          .orElseThrow(() -> new BaseException(PaymentErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));
      Point3RefundResult result = refreshPoint3Refund(paymentAttempt, latest);
      applyRefundResult(
          order, latest, sellerUserId, latest.getReason(), result, Instant.now(clock));
      refundPersistencePort.save(latest);
      return toDetail(order);
    }
    if (latest.getStatus() == RefundStatus.COMPLETED) {
      throw new BaseException(OrderErrorCode.ORDER_REFUND_ALREADY_PROCESSED);
    }
    return toDetail(order);
  }

  private Point3RefundResult requestPoint3Refund(
      PaymentAttempt paymentAttempt, Refund refund, String reason) {
    try {
      return point3PaymentPort.refund(
          paymentAttempt.getPoint3SessionId(),
          refund.getAmount(),
          reason,
          refund.getId().toString());
    } catch (Point3PaymentException exception) {
      return Point3RefundResult.processing(
          null, exception.getFailureCode(), exception.getMessage(), null);
    }
  }

  private Point3RefundResult refreshPoint3Refund(PaymentAttempt paymentAttempt, Refund refund) {
    Point3RefundStatusResult status;
    try {
      status = point3PaymentPort.getRefundStatus(paymentAttempt.getPoint3SessionId());
    } catch (Point3PaymentException exception) {
      return Point3RefundResult.processing(
          refund.getProviderRefundId(), exception.getFailureCode(), exception.getMessage(), null);
    }
    Point3RefundResult result = findPoint3Refund(status, refund);
    if (result.outcome() != RefundOutcome.PROCESSING) {
      return result;
    }
    Point3RefundStatusResult resumed;
    try {
      resumed = point3PaymentPort.resumeRefund(paymentAttempt.getPoint3SessionId());
    } catch (Point3PaymentException exception) {
      return Point3RefundResult.processing(
          refund.getProviderRefundId(), exception.getFailureCode(), exception.getMessage(), null);
    }
    return resumed.findRefund(refund.getProviderRefundId()).orElse(result);
  }

  private Point3RefundResult findPoint3Refund(Point3RefundStatusResult status, Refund refund) {
    if (status.hasAmbiguousRefundsWithoutProviderId(refund.getProviderRefundId())) {
      return Point3RefundResult.processing(
          null,
          "POINT3_REFUND_MATCH_AMBIGUOUS",
          "Point3 refund status contains multiple refunds but local refund has no provider id",
          null);
    }
    return status
        .findRefund(refund.getProviderRefundId())
        .orElseGet(() -> Point3RefundResult.processing(
            refund.getProviderRefundId(), "POINT3_REFUND_PROCESSING", null, null));
  }

  private void applyRefundResult(
      Order order,
      Refund refund,
      UUID changedBy,
      String reason,
      Point3RefundResult result,
      Instant processingAt) {
    switch (result.outcome()) {
      case COMPLETED ->
        completeRefund(order, refund, changedBy, reason, result.providerRefundId(), processingAt);
      case PROCESSING -> {
        refund.keepProcessing(
            result.providerRefundId(),
            result.failureCode(),
            result.failureMessage(),
            result.failureDetails());
      }
      case RETRYABLE, MANUAL_REQUIRED, FAILED ->
        refund.fail(
            result.outcome(),
            result.providerRefundId(),
            result.failureCode(),
            result.failureMessage(),
            result.failureDetails(),
            processingAt);
    }
  }

  private Order getSellerOrder(UUID orderId, UUID storeId) {
    return orderPersistencePort
        .findByIdAndStoreId(orderId, storeId)
        .orElseThrow(() -> new BaseException(OrderErrorCode.ORDER_NOT_FOUND));
  }

  private Order getSellerOrderForUpdate(UUID orderId, UUID storeId) {
    return orderPersistencePort
        .findByIdAndStoreIdForUpdate(orderId, storeId)
        .orElseThrow(() -> new BaseException(OrderErrorCode.ORDER_NOT_FOUND));
  }

  private void changeStatus(Order order, UUID changedBy, String reason, Runnable transition) {
    changeStatus(order, changedBy, reason, Instant.now(clock), transition);
  }

  private void changeStatus(
      Order order, UUID changedBy, String reason, Instant changedAt, Runnable transition) {
    OrderStatus previousStatus = order.getStatus();
    try {
      transition.run();
    } catch (IllegalStateException e) {
      throw new BaseException(OrderErrorCode.ORDER_STATUS_FORBIDDEN);
    }
    if (previousStatus != order.getStatus()) {
      orderStatusHistoryPersistencePort.save(OrderStatusHistory.create(
          order.getId(), previousStatus, order.getStatus(), changedBy, reason, changedAt));
    }
  }

  private void notifySellerRefundRequested(Order order) {
    Store store = storePersistencePort
        .findById(order.getStoreId())
        .orElseThrow(() -> new BaseException(OrderErrorCode.ORDER_NOT_FOUND));
    notificationCreateUseCase.create(new CreateNotificationCommand(
        store.getOwnerUserId(),
        NotificationType.ORDER_REFUND_REQUESTED,
        NotificationReferenceType.ORDER,
        order.getId(),
        "주문 환불이 요청되었습니다.",
        "환불 요청 주문을 확인해 주세요."));
  }

  private void notifyBuyerRefundCompleted(Order order) {
    notificationCreateUseCase.create(new CreateNotificationCommand(
        order.getBuyerUserId(),
        NotificationType.ORDER_REFUNDED,
        NotificationReferenceType.ORDER,
        order.getId(),
        "주문 환불이 완료되었습니다.",
        "환불 내역을 확인해 주세요."));
  }

  private OrderDetailResult toDetail(Order order) {
    Instant now = Instant.now(clock);
    PaymentAttempt paymentAttempt = paymentAttemptPersistencePort
        .findById(order.getPaymentAttemptId())
        .orElseThrow(() -> new BaseException(PaymentErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));
    List<RefundResult> refunds = refundPersistencePort.findAllByOrderId(order.getId()).stream()
        .map(RefundResult::from)
        .toList();
    var confirmation = orderConfirmationPersistencePort
        .findById(order.getConfirmationId())
        .orElseThrow(
            () -> new BaseException(OrderConfirmationErrorCode.ORDER_CONFIRMATION_NOT_FOUND));

    return OrderDetailResult.of(
        toResult(order),
        PaymentAttemptResult.from(paymentAttempt, now),
        refunds,
        orderOptionRowResolver.fromConfirmation(confirmation));
  }

  private OrderResult toResult(Order order) {
    return OrderResult.from(
        order,
        orderReferenceAssetDeliveryService.appendDeliveries(order.getStartReferenceAssets()));
  }
}
