package io.point3.p3api.inquiry.application.submission.view;

import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.OrderFormErrorCode;
import io.point3.p3api.inquiry.application.chat.InquiryChatAccessService;
import io.point3.p3api.inquiry.application.port.OrderFormSubmissionPersistencePort;
import io.point3.p3api.inquiry.application.realtime.InquiryListChangeEventPublisher;
import io.point3.p3api.inquiry.application.submission.query.OrderFormAnswerDeliveryService;
import io.point3.p3api.inquiry.application.submission.query.OrderFormReferenceAssetDeliveryService;
import io.point3.p3api.inquiry.application.submission.result.OrderFormSubmissionResult;
import io.point3.p3api.inquiry.domain.entity.Inquiry;
import io.point3.p3api.inquiry.domain.entity.OrderFormSubmission;
import io.point3.p3api.order.application.option.OrderOptionRowResolver;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SellerOrderFormSubmissionViewService implements SellerOrderFormSubmissionViewUseCase {

  private final InquiryChatAccessService inquiryChatAccessService;
  private final OrderFormSubmissionPersistencePort orderFormSubmissionPersistencePort;
  private final OrderFormAnswerDeliveryService orderFormAnswerDeliveryService;
  private final OrderFormReferenceAssetDeliveryService orderFormReferenceAssetDeliveryService;
  private final OrderOptionRowResolver orderOptionRowResolver;
  private final InquiryListChangeEventPublisher inquiryListChangeEventPublisher;
  private final Clock clock;

  @Override
  public OrderFormSubmissionResult markViewed(UUID inquiryId, UUID submissionId, UUID storeId) {
    Inquiry inquiry = inquiryChatAccessService.getSellerInquiry(inquiryId, storeId);
    OrderFormSubmission submission = orderFormSubmissionPersistencePort
        .findByIdForUpdate(submissionId)
        .orElseThrow(() -> new BaseException(OrderFormErrorCode.ORDER_FORM_NOT_FOUND));

    validate(submission, inquiry);

    Instant previousViewedAt = submission.getSellerViewedAt();
    submission.markSellerViewed(Instant.now(clock));
    boolean inquiryStatusChanged = inquiry.markReviewIfCurrent(submission.getId());

    if (previousViewedAt == null || inquiryStatusChanged) {
      inquiryListChangeEventPublisher.publishInquiryChanged(inquiry.getId());
    }

    return toResult(submission, inquiry.getCurrentOrderFormSubmissionId());
  }

  private static void validate(OrderFormSubmission submission, Inquiry inquiry) {
    if (!submission.getInquiryId().equals(inquiry.getId())) {
      throw new BaseException(OrderFormErrorCode.ORDER_FORM_NOT_FOUND);
    }
  }

  private OrderFormSubmissionResult toResult(
      OrderFormSubmission submission, UUID currentOrderFormSubmissionId) {
    String answers = orderFormAnswerDeliveryService.appendImageDeliveries(submission.getAnswers());
    return OrderFormSubmissionResult.from(
        submission,
        answers,
        orderFormReferenceAssetDeliveryService.appendDeliveries(submission.getReferenceAssets()),
        orderOptionRowResolver.fromSubmissionAnswers(answers),
        currentOrderFormSubmissionId);
  }
}
