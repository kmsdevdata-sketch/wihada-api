package io.point3.p3api.order.application.query;

import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.OrderConfirmationErrorCode;
import io.point3.p3api.inquiry.application.chat.InquiryChatAccessService;
import io.point3.p3api.inquiry.application.port.OrderFormSubmissionPersistencePort;
import io.point3.p3api.inquiry.domain.entity.Inquiry;
import io.point3.p3api.inquiry.domain.entity.OrderFormSubmission;
import io.point3.p3api.order.application.price.OrderConfirmationPriceCalculator;
import io.point3.p3api.orderform.application.query.OrderFormQueryUseCase;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 최신 제출 주문서 기반 주문확인서 미리보기 조회 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderConfirmationPreviewQueryService {

  private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

  private final InquiryChatAccessService inquiryChatAccessService;
  private final OrderFormSubmissionPersistencePort submissionPersistencePort;
  private final OrderFormQueryUseCase orderFormQueryUseCase;
  private final OrderConfirmationPriceCalculator priceCalculator;

  public OrderConfirmationPreview getPreview(UUID inquiryId, UUID storeId) {
    return getPreview(inquiryId, storeId, null);
  }

  public OrderConfirmationPreview getPreview(
      UUID inquiryId, UUID storeId, UUID orderFormSubmissionId) {
    Inquiry inquiry = inquiryChatAccessService.getSellerInquiry(inquiryId, storeId);
    OrderFormSubmission submission = findSubmission(inquiry, orderFormSubmissionId);
    validateSellerViewed(submission);

    var pricePreview = priceCalculator.preview(submission.getAnswers());
    return new OrderConfirmationPreview(
        submission.getId(),
        orderFormQueryUseCase
            .getSellerTemplate(storeId, submission.getTemplateId())
            .name(),
        submission
            .getPickupDate()
            .atTime(submission.getPickupTime())
            .atZone(KOREA_ZONE_ID)
            .toInstant(),
        submission.getAnswers(),
        pricePreview.baseAmount(),
        !pricePreview.unconfirmedOptions().isEmpty(),
        !pricePreview.unconfirmedOptions().isEmpty(),
        pricePreview.unconfirmedOptions());
  }

  private OrderFormSubmission findSubmission(Inquiry inquiry, UUID orderFormSubmissionId) {
    if (orderFormSubmissionId == null) {
      return java.util.Optional.ofNullable(inquiry.getCurrentOrderFormSubmissionId())
          .flatMap(submissionPersistencePort::findById)
          .orElseThrow(() ->
              new BaseException(OrderConfirmationErrorCode.ORDER_CONFIRMATION_SUBMISSION_INVALID));
    }

    OrderFormSubmission submission = submissionPersistencePort
        .findById(orderFormSubmissionId)
        .orElseThrow(() ->
            new BaseException(OrderConfirmationErrorCode.ORDER_CONFIRMATION_SUBMISSION_INVALID));

    if (!submission.getInquiryId().equals(inquiry.getId())) {
      throw new BaseException(OrderConfirmationErrorCode.ORDER_CONFIRMATION_SUBMISSION_INVALID);
    }

    return submission;
  }

  private void validateSellerViewed(OrderFormSubmission submission) {
    if (!submission.isSellerViewed()) {
      throw new BaseException(OrderConfirmationErrorCode.ORDER_CONFIRMATION_SUBMISSION_NOT_VIEWED);
    }
  }
}
