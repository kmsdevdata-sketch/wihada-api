package io.point3.p3api.payment.application;

import io.point3.p3api.chat.application.timeline.ChatTimelineItemPublisher;
import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.CommonErrorCode;
import io.point3.p3api.exception.code.OrderConfirmationErrorCode;
import io.point3.p3api.exception.code.PaymentErrorCode;
import io.point3.p3api.inquiry.application.chat.InquiryChatAccessService;
import io.point3.p3api.inquiry.application.startreference.OrderStartReferenceAssetService;
import io.point3.p3api.inquiry.domain.entity.Inquiry;
import io.point3.p3api.notification.application.create.CreateNotificationCommand;
import io.point3.p3api.notification.application.create.NotificationCreateUseCase;
import io.point3.p3api.notification.domain.type.NotificationReferenceType;
import io.point3.p3api.notification.domain.type.NotificationType;
import io.point3.p3api.order.application.port.OrderConfirmationPersistencePort;
import io.point3.p3api.order.application.port.OrderPersistencePort;
import io.point3.p3api.order.application.port.OrderStatusHistoryPersistencePort;
import io.point3.p3api.order.domain.entity.Order;
import io.point3.p3api.order.domain.entity.OrderConfirmation;
import io.point3.p3api.order.domain.entity.OrderStatusHistory;
import io.point3.p3api.order.domain.type.OrderConfirmationStatus;
import io.point3.p3api.payment.application.capture.CapturePaymentCommand;
import io.point3.p3api.payment.application.capture.PaymentCaptureUseCase;
import io.point3.p3api.payment.application.port.PaymentAttemptPersistencePort;
import io.point3.p3api.payment.application.port.Point3PaymentException;
import io.point3.p3api.payment.application.port.Point3PaymentPort;
import io.point3.p3api.payment.application.prepare.PaymentPrepareUseCase;
import io.point3.p3api.payment.application.prepare.PreparePaymentCommand;
import io.point3.p3api.payment.application.result.PaymentCaptureResult;
import io.point3.p3api.payment.application.result.PaymentPreparationResult;
import io.point3.p3api.payment.application.result.Point3CaptureResult;
import io.point3.p3api.payment.application.result.Point3PaymentSession;
import io.point3.p3api.payment.config.Point3Properties;
import io.point3.p3api.payment.domain.entity.PaymentAttempt;
import io.point3.p3api.store.application.port.StorePersistencePort;
import io.point3.p3api.store.domain.entity.Store;
import io.point3.p3api.user.application.port.UserPersistencePort;
import io.point3.p3api.user.domain.entity.User;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentService implements PaymentPrepareUseCase, PaymentCaptureUseCase {

  private static final DateTimeFormatter ORDER_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

  private final InquiryChatAccessService inquiryChatAccessService;
  private final OrderConfirmationPersistencePort orderConfirmationPersistencePort;
  private final PaymentAttemptPersistencePort paymentAttemptPersistencePort;
  private final OrderPersistencePort orderPersistencePort;
  private final OrderStatusHistoryPersistencePort orderStatusHistoryPersistencePort;
  private final UserPersistencePort userPersistencePort;
  private final StorePersistencePort storePersistencePort;
  private final OrderStartReferenceAssetService orderStartReferenceAssetService;
  private final NotificationCreateUseCase notificationCreateUseCase;
  private final ChatTimelineItemPublisher chatTimelineItemPublisher;
  private final Point3PaymentPort point3PaymentPort;
  private final Point3Properties point3Properties;
  private final Clock clock;

  @Override
  public PaymentPreparationResult prepare(PreparePaymentCommand command) {
    inquiryChatAccessService.getBuyerInquiry(command.inquiryId(), command.buyerUserId());

    OrderConfirmation confirmation = getPayableConfirmation(command);

    User payer = userPersistencePort
        .findById(command.buyerUserId())
        .orElseThrow(() -> new BaseException(CommonErrorCode.UNAUTHORIZED));

    Optional<PaymentAttempt> activePaymentAttempt = findActivePaymentAttempt(command);
    if (activePaymentAttempt.isPresent()) {
      PaymentAttempt paymentAttempt = activePaymentAttempt.get();
      log.info(
          "Reuse active payment attempt. confirmationId={} ,paymentAttemptId={} ,status={} ,expiresAt={}",
          paymentAttempt.getConfirmationId(),
          paymentAttempt.getId(),
          paymentAttempt.getStatus(),
          paymentAttempt.getExpiresAt());
      return buildPreparation(paymentAttempt, payer.getPayerId(), confirmation.getMenuName());
    }

    Point3PaymentSession session = createPoint3Session(confirmation);

    if (session.amount() != confirmation.getAmount()) {
      throw new BaseException(PaymentErrorCode.PAYMENT_EXTERNAL_UNAVAILABLE);
    }

    Instant expiresAt = Instant.now(clock).plus(point3Properties.sessionTtl());

    PaymentAttempt paymentAttempt = paymentAttemptPersistencePort.save(PaymentAttempt.create(
        confirmation.getId(),
        command.buyerUserId(),
        session.sessionId(),
        payer.getPayerId(),
        confirmation.getAmount(),
        expiresAt));

    return buildPreparation(paymentAttempt, payer.getPayerId(), confirmation.getMenuName());
  }

  @Override
  public PaymentCaptureResult capture(CapturePaymentCommand command) {
    validateCaptureMessage(command);

    PaymentAttempt paymentAttempt = getPaymentAttempt(command.paymentAttemptId());

    validateCaptureOwner(paymentAttempt, command.buyerUserId());
    validateSession(paymentAttempt, command.sessionId());

    Optional<Order> existingOrder =
        orderPersistencePort.findByPaymentAttemptId(paymentAttempt.getId());

    if (paymentAttempt.needsConfirmation()) {
      return reconcilePayment(paymentAttempt, command.payerId());
    }

    if (!paymentAttempt.isReady()) {
      return PaymentCaptureResult.of(
          paymentAttempt, existingOrder.map(Order::getId).orElse(null));
    }

    if (Instant.now(clock).isAfter(paymentAttempt.getExpiresAt())) {
      paymentAttempt.fail("PAYMENT_SESSION_EXPIRED", Instant.now(clock));
      return PaymentCaptureResult.of(paymentAttempt, null);
    }

    paymentAttempt.startCapture();
    Point3CaptureResult captureResult = requestCapture(paymentAttempt.getPoint3SessionId());
    validatePoint3Session(paymentAttempt, captureResult);

    return applyCaptureResult(paymentAttempt, command.payerId(), captureResult);
  }

  private PaymentCaptureResult reconcilePayment(PaymentAttempt paymentAttempt, String payerId) {
    Point3CaptureResult sessionResult = requestSession(paymentAttempt.getPoint3SessionId());
    validatePoint3Session(paymentAttempt, sessionResult);
    return applyCaptureResult(paymentAttempt, payerId, sessionResult);
  }

  private PaymentCaptureResult applyCaptureResult(
      PaymentAttempt paymentAttempt, String payerId, Point3CaptureResult captureResult) {
    if (captureResult.status() == Point3CaptureResult.Status.CAPTURED) {
      return completePayment(paymentAttempt, payerId);
    }

    Instant completedAt = Instant.now(clock);

    if (captureResult.status() == Point3CaptureResult.Status.FAILED) {
      paymentAttempt.fail(captureResult.failureCode(), completedAt);
    } else {
      paymentAttempt.needConfirmation(captureResult.failureCode(), completedAt);
    }

    return PaymentCaptureResult.of(paymentAttempt, null);
  }

  private OrderConfirmation getPayableConfirmation(PreparePaymentCommand command) {
    OrderConfirmation confirmation = getConfirmation(command.inquiryId(), command.confirmationId());

    Optional<OrderConfirmation> latestSentConfirmation =
        orderConfirmationPersistencePort.findLatestByInquiryIdAndStatus(
            command.inquiryId(), OrderConfirmationStatus.SENT);

    boolean payable = latestSentConfirmation
        .map(latest -> latest.getId().equals(confirmation.getId()))
        .orElse(false);

    if (!payable || confirmation.getBuyerViewedAt() == null || confirmation.getAmount() <= 0) {
      throw new BaseException(PaymentErrorCode.PAYMENT_CONFIRMATION_NOT_PAYABLE);
    }

    return confirmation;
  }

  private OrderConfirmation getConfirmation(UUID inquiryId, UUID confirmationId) {
    OrderConfirmation confirmation = orderConfirmationPersistencePort
        .findByIdForUpdate(confirmationId)
        .orElseThrow(
            () -> new BaseException(OrderConfirmationErrorCode.ORDER_CONFIRMATION_NOT_FOUND));

    if (!confirmation.getInquiryId().equals(inquiryId)) {
      throw new BaseException(OrderConfirmationErrorCode.ORDER_CONFIRMATION_NOT_FOUND);
    }

    return confirmation;
  }

  private Optional<PaymentAttempt> findActivePaymentAttempt(PreparePaymentCommand command) {
    Instant now = Instant.now(clock);

    return paymentAttemptPersistencePort.findAllByConfirmationId(command.confirmationId()).stream()
        .filter(paymentAttempt -> paymentAttempt.getPayerUserId().equals(command.buyerUserId()))
        .filter(paymentAttempt -> paymentAttempt.isActive(now))
        .findFirst();
  }

  private Point3PaymentSession createPoint3Session(OrderConfirmation confirmation) {
    try {
      return point3PaymentPort.createSession(
          confirmation.getAmount(),
          confirmation.getMenuName(),
          confirmation.getStoreNameSnapshot());
    } catch (Point3PaymentException e) {
      log.warn(
          "Point3 payment session creation failed. confirmationId={} ,failureCode={} ,message={}",
          confirmation.getId(),
          e.getFailureCode(),
          e.getMessage());
      throw new BaseException(PaymentErrorCode.PAYMENT_EXTERNAL_UNAVAILABLE);
    }
  }

  private PaymentPreparationResult buildPreparation(
      PaymentAttempt paymentAttempt, String payerId, String orderName) {
    String authnState = paymentAttempt.getId().toString();
    String entryPath = "/";
    String authenticationUrl = Point3AuthenticationUrlFactory.build(
        point3Properties, paymentAttempt.getPoint3SessionId(), payerId);

    return new PaymentPreparationResult(
        paymentAttempt.getId(),
        paymentAttempt.getPoint3SessionId(),
        paymentAttempt.getAmount(),
        payerId,
        point3Properties.clientId(),
        orderName,
        point3Properties.clientId(),
        authnState,
        entryPath,
        authenticationUrl,
        point3Properties.paymentOrigin(),
        paymentAttempt.getExpiresAt());
  }

  private void validateCaptureMessage(CapturePaymentCommand command) {
    if (isBlank(command.sessionId()) || isBlank(command.payerId())) {
      throw new BaseException(PaymentErrorCode.PAYMENT_CAPTURE_MESSAGE_INVALID);
    }
  }

  private PaymentAttempt getPaymentAttempt(UUID paymentAttemptId) {
    return paymentAttemptPersistencePort
        .findByIdForUpdate(paymentAttemptId)
        .orElseThrow(() -> new BaseException(PaymentErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));
  }

  private void validateCaptureOwner(PaymentAttempt paymentAttempt, UUID buyerUserId) {
    if (!paymentAttempt.getPayerUserId().equals(buyerUserId)) {
      throw new BaseException(PaymentErrorCode.PAYMENT_ATTEMPT_FORBIDDEN);
    }
  }

  private void validateSession(PaymentAttempt paymentAttempt, String sessionId) {
    if (!paymentAttempt.getPoint3SessionId().equals(sessionId)) {
      throw new BaseException(PaymentErrorCode.PAYMENT_SESSION_MISMATCH);
    }
  }

  private Point3CaptureResult requestCapture(String sessionId) {
    try {
      return point3PaymentPort.capture(sessionId);
    } catch (Point3PaymentException e) {
      return new Point3CaptureResult(
          sessionId, Point3CaptureResult.Status.PROCESSING, e.getFailureCode());
    }
  }

  private Point3CaptureResult requestSession(String sessionId) {
    try {
      return point3PaymentPort.getSession(sessionId);
    } catch (Point3PaymentException e) {
      return new Point3CaptureResult(
          sessionId, Point3CaptureResult.Status.PROCESSING, e.getFailureCode());
    }
  }

  private void validatePoint3Session(
      PaymentAttempt paymentAttempt, Point3CaptureResult captureResult) {
    if (!paymentAttempt.getPoint3SessionId().equals(captureResult.sessionId())) {
      throw new BaseException(PaymentErrorCode.PAYMENT_SESSION_MISMATCH);
    }
  }

  private PaymentCaptureResult completePayment(PaymentAttempt paymentAttempt, String payerId) {
    Instant completedAt = Instant.now(clock);

    OrderConfirmation confirmation = orderConfirmationPersistencePort
        .findById(paymentAttempt.getConfirmationId())
        .orElseThrow(
            () -> new BaseException(OrderConfirmationErrorCode.ORDER_CONFIRMATION_NOT_FOUND));

    Inquiry inquiry = inquiryChatAccessService.getBuyerInquiry(
        confirmation.getInquiryId(), paymentAttempt.getPayerUserId());

    User payer = userPersistencePort
        .findById(paymentAttempt.getPayerUserId())
        .orElseThrow(() -> new BaseException(CommonErrorCode.UNAUTHORIZED));

    paymentAttempt.succeed(payerId, completedAt);
    payer.connectPayer(payerId);
    confirmation.markPaid();
    inquiry.markPaidIfCurrent(confirmation.getOrderFormSubmissionId());
    String startReferenceAssets = orderStartReferenceAssetService.createOrderReferenceAssetSnapshot(
        confirmation.getInquiryId());

    Order order = orderPersistencePort
        .findByPaymentAttemptId(paymentAttempt.getId())
        .orElseGet(() -> orderPersistencePort.save(Order.create(
            inquiry.getStoreId(),
            paymentAttempt.getPayerUserId(),
            confirmation.getInquiryId(),
            confirmation.getId(),
            paymentAttempt.getId(),
            createOrderNumber(paymentAttempt),
            confirmation.getMenuName(),
            confirmation.getOptionSummary(),
            startReferenceAssets,
            paymentAttempt.getAmount(),
            confirmation.getPickupAt())));
    orderStatusHistoryPersistencePort.save(OrderStatusHistory.create(
        order.getId(),
        null,
        order.getStatus(),
        paymentAttempt.getPayerUserId(),
        "PAYMENT_SUCCEEDED",
        completedAt));
    orderStartReferenceAssetService.clear(inquiry.getId());

    notifyPaymentCompleted(inquiry, order);
    chatTimelineItemPublisher.publishPaymentCompleted(
        inquiry.getId(), paymentAttempt.getPayerUserId(), order.getId());

    return PaymentCaptureResult.of(paymentAttempt, order.getId());
  }

  private void notifyPaymentCompleted(Inquiry inquiry, Order order) {
    Store store = storePersistencePort
        .findById(inquiry.getStoreId())
        .orElseThrow(() -> new BaseException(CommonErrorCode.INTERNAL_SERVER_ERROR));
    notificationCreateUseCase.create(new CreateNotificationCommand(
        inquiry.getBuyerUserId(),
        NotificationType.PAYMENT_COMPLETED,
        NotificationReferenceType.ORDER,
        order.getId(),
        "결제가 완료되었습니다.",
        "주문 내역을 확인해 주세요."));
    notificationCreateUseCase.create(new CreateNotificationCommand(
        store.getOwnerUserId(),
        NotificationType.PAYMENT_COMPLETED,
        NotificationReferenceType.ORDER,
        order.getId(),
        "결제가 완료되었습니다.",
        "새 주문을 확인해 주세요."));
  }

  private String createOrderNumber(PaymentAttempt paymentAttempt) {
    String date =
        ORDER_DATE_FORMATTER.format(Instant.now(clock).atZone(clock.getZone()).toLocalDate());
    String suffix = paymentAttempt.getId().toString().replace("-", "").substring(0, 27);
    return "P3-" + date + "-" + suffix;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
