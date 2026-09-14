package io.point3.p3api.inquiry.application.submission.create;

import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.OrderFormErrorCode;
import io.point3.p3api.inquiry.application.command.CreateOrderFormSubmissionCommand;
import io.point3.p3api.inquiry.application.port.OrderFormSubmissionPersistencePort;
import io.point3.p3api.inquiry.application.submission.snapshot.OrderFormAnswerSnapshotFactory;
import io.point3.p3api.inquiry.application.submission.snapshot.OrderFormReferenceSnapshotFactory;
import io.point3.p3api.inquiry.application.submission.validation.OrderFormAnswerValidator;
import io.point3.p3api.inquiry.application.submission.validation.OrderFormImageAssetValidator;
import io.point3.p3api.inquiry.application.submission.validation.OrderFormPickupValidator;
import io.point3.p3api.inquiry.application.submission.validation.OrderFormReferenceAssetValidator;
import io.point3.p3api.inquiry.domain.entity.OrderFormSubmission;
import io.point3.p3api.notification.application.create.CreateNotificationCommand;
import io.point3.p3api.notification.application.create.NotificationCreateUseCase;
import io.point3.p3api.notification.application.email.OrderFormWaitingEmailEvent;
import io.point3.p3api.notification.domain.type.NotificationReferenceType;
import io.point3.p3api.notification.domain.type.NotificationType;
import io.point3.p3api.order.application.port.OrderConfirmationPersistencePort;
import io.point3.p3api.order.domain.type.OrderConfirmationStatus;
import io.point3.p3api.orderform.application.query.OrderFormQueryUseCase;
import io.point3.p3api.orderform.application.result.OrderFormResult;
import io.point3.p3api.store.application.port.StorePersistencePort;
import io.point3.p3api.store.domain.entity.Store;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** 주문서 제출 검증/스냅샷/저장 담당 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFormSubmissionService implements OrderFormSubmissionCreateUseCase {

  private final OrderFormQueryUseCase orderFormQueryUseCase;
  private final OrderFormSubmissionPersistencePort submissionPersistencePort;
  private final OrderFormAnswerValidator orderFormAnswerValidator;
  private final OrderFormReferenceAssetValidator orderFormReferenceAssetValidator;
  private final OrderFormPickupValidator orderFormPickupValidator;
  private final OrderFormImageAssetValidator orderFormImageAssetValidator;
  private final OrderConfirmationPersistencePort orderConfirmationPersistencePort;
  private final OrderFormAnswerSnapshotFactory snapshotFactory;
  private final OrderFormReferenceSnapshotFactory referenceSnapshotFactory;
  private final StorePersistencePort storePersistencePort;
  private final NotificationCreateUseCase notificationCreateUseCase;
  private final ApplicationEventPublisher applicationEventPublisher;

  @Override
  public OrderFormSubmission create(CreateOrderFormSubmissionCommand command) {
    OrderFormResult activeForm = orderFormQueryUseCase.getActiveTemplate(command.storeId());

    validateOrderFormSubmissionRequirements(command, activeForm);

    orderFormAnswerValidator.validate(activeForm.optionGroups(), command.formAnswers());
    orderFormImageAssetValidator.validate(
        activeForm.optionGroups(), command.formAnswers(), command.buyerUserId());
    orderFormPickupValidator.validate(command.storeId(), command.pickupRequest());
    orderFormReferenceAssetValidator.validate(
        command.storeId(), command.referenceAssets(), command.buyerUserId());

    String answersSnapshot =
        snapshotFactory.create(activeForm.optionGroups(), command.formAnswers());

    String referenceAssets = referenceSnapshotFactory.create(command.referenceAssets());

    OrderFormSubmission submission = OrderFormSubmission.create(
        command.inquiryId(),
        activeForm.id(),
        command.buyerUserId(),
        command.pickupRequest().pickupDate(),
        command.pickupRequest().pickupTime(),
        answersSnapshot,
        referenceAssets,
        command.cancellationRefundAgreement().agreed());

    OrderFormSubmission savedSubmission = submissionPersistencePort.save(submission);
    orderConfirmationPersistencePort
        .findLatestByInquiryIdAndStatus(command.inquiryId(), OrderConfirmationStatus.SENT)
        .ifPresent(confirmation -> confirmation.replace());
    Store store = findStore(command.storeId());
    notifySeller(command, command.update(), store);
    log.info(
        "Publish order form waiting email event. submissionId={}, inquiryId={}, sellerUserId={}, isUpdate={}",
        savedSubmission.getId(),
        command.inquiryId(),
        store.getOwnerUserId(),
        command.update());
    applicationEventPublisher.publishEvent(new OrderFormWaitingEmailEvent(
        store.getOwnerUserId(), command.inquiryId(), savedSubmission.getId()));
    return savedSubmission;
  }

  private Store findStore(UUID storeId) {
    return storePersistencePort
        .findById(storeId)
        .orElseThrow(() -> new BaseException(OrderFormErrorCode.ORDER_FORM_NOT_FOUND));
  }

  private void notifySeller(
      CreateOrderFormSubmissionCommand command, boolean isUpdate, Store store) {
    notificationCreateUseCase.create(new CreateNotificationCommand(
        store.getOwnerUserId(),
        isUpdate ? NotificationType.ORDER_FORM_UPDATED : NotificationType.ORDER_FORM_SUBMITTED,
        NotificationReferenceType.INQUIRY,
        command.inquiryId(),
        isUpdate ? "주문서가 수정되었습니다." : "주문서가 접수되었습니다.",
        "제출 주문서를 확인해 주세요."));
  }

  private static void validateOrderFormSubmissionRequirements(
      CreateOrderFormSubmissionCommand command, OrderFormResult activeForm) {
    if (!activeForm.id().equals(command.orderFormTemplateId())) {
      throw new BaseException(OrderFormErrorCode.ORDER_FORM_NOT_FOUND);
    }

    if (!command.noticeAgreement().agreed()) {
      throw new BaseException(OrderFormErrorCode.ORDER_FORM_NOTICE_AGREEMENT_REQUIRED);
    }
  }
}
