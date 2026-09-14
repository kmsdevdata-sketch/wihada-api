package io.point3.p3api.inquiry.application.submission.query;

import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.OrderFormErrorCode;
import io.point3.p3api.inquiry.application.chat.InquiryChatAccessService;
import io.point3.p3api.inquiry.application.port.OrderFormSubmissionPersistencePort;
import io.point3.p3api.inquiry.application.submission.result.OrderFormSubmissionResult;
import io.point3.p3api.inquiry.domain.entity.Inquiry;
import io.point3.p3api.inquiry.domain.entity.OrderFormSubmission;
import io.point3.p3api.order.application.option.OrderOptionRowResolver;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuyerOrderFormSubmissionQueryService implements BuyerOrderFormSubmissionQueryUseCase {

  private final InquiryChatAccessService inquiryChatAccessService;
  private final OrderFormSubmissionPersistencePort orderFormSubmissionPersistencePort;
  private final OrderFormAnswerDeliveryService orderFormAnswerDeliveryService;
  private final OrderFormReferenceAssetDeliveryService orderFormReferenceAssetDeliveryService;
  private final OrderOptionRowResolver orderOptionRowResolver;

  @Override
  public OrderFormSubmissionResult getSubmission(
      UUID inquiryId, UUID submissionId, UUID buyerUserId) {
    Inquiry inquiry = inquiryChatAccessService.getBuyerInquiry(inquiryId, buyerUserId);
    OrderFormSubmission submission = orderFormSubmissionPersistencePort
        .findById(submissionId)
        .orElseThrow(() -> new BaseException(OrderFormErrorCode.ORDER_FORM_NOT_FOUND));

    validate(submission, inquiry, buyerUserId);
    return toResult(submission, inquiry.getCurrentOrderFormSubmissionId());
  }

  private static void validate(OrderFormSubmission submission, Inquiry inquiry, UUID buyerUserId) {
    if (!submission.getInquiryId().equals(inquiry.getId())
        || !submission.getSubmittedBy().equals(buyerUserId)) {
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
