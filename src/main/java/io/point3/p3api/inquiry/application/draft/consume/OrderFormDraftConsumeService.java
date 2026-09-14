package io.point3.p3api.inquiry.application.draft.consume;

import io.point3.p3api.chat.application.timeline.ChatTimelineItemPublisher;
import io.point3.p3api.exception.BaseException;
import io.point3.p3api.exception.code.OrderFormErrorCode;
import io.point3.p3api.inquiry.application.command.ConsumeOrderFormDraftCommand;
import io.point3.p3api.inquiry.application.command.CreateOrderFormSubmissionCommand;
import io.point3.p3api.inquiry.application.command.OpenInquiryCommand;
import io.point3.p3api.inquiry.application.draft.model.OrderFormDraftData;
import io.point3.p3api.inquiry.application.open.OpenInquiryUseCase;
import io.point3.p3api.inquiry.application.port.OrderFormDraftStorePort;
import io.point3.p3api.inquiry.application.result.OrderFormDraftConsumeResult;
import io.point3.p3api.inquiry.application.startreference.OrderStartReferenceAssetService;
import io.point3.p3api.inquiry.application.submission.create.OrderFormSubmissionCreateUseCase;
import io.point3.p3api.inquiry.application.submission.validation.OrderFormReferenceAssetValidator;
import io.point3.p3api.inquiry.domain.entity.Inquiry;
import io.point3.p3api.inquiry.domain.entity.OrderFormSubmission;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 전에 저장해둔 주문서 draft를 -> 로그인 후 실제 채팅방 주문서 제출로 전환하는 오케스트레이터
 */
@Service
@Transactional
@RequiredArgsConstructor
public class OrderFormDraftConsumeService implements OrderFormDraftConsumeUseCase {

  private final OrderFormDraftStorePort orderFormDraftStorePort;
  private final OpenInquiryUseCase openInquiryUseCase;
  private final OrderFormSubmissionCreateUseCase orderFormSubmissionCreateUseCase;
  private final OrderStartReferenceAssetService orderStartReferenceAssetService;
  private final OrderFormReferenceAssetValidator orderFormReferenceAssetValidator;
  private final ChatTimelineItemPublisher timelineItemPublisher;

  @Override
  public OrderFormDraftConsumeResult consume(ConsumeOrderFormDraftCommand command) {

    // draftKey를 통해 제출 데이터 가져오기
    OrderFormDraftData draft = orderFormDraftStorePort
        .findByDraftKey(command.draftKey())
        .orElseThrow(() ->
            new BaseException(OrderFormErrorCode.ORDER_FORM_NOT_FOUND)); // TODO : 정확한 에러로 변경 필요

    // 사전 제출 데이터의 스토어ID와 요청 커맨드의 구매자ID를 통해 채팅방 개설
    Inquiry inquiry =
        openInquiryUseCase.open(OpenInquiryCommand.of(draft.storeId(), command.buyerUserId()));

    validateStartReferenceAssets(draft, command.buyerUserId());
    orderStartReferenceAssetService.replaceIfPresent(
        inquiry.getId(),
        command.buyerUserId(),
        draft.startReferenceAsset(),
        draft.startReferenceAssetProvided());

    // 사전 제출 데이터를 주문서제출 커맨드와 매핑하여 주문서 생성
    OrderFormSubmission submission = orderFormSubmissionCreateUseCase.create(
        toCreateSubmissionCommand(draft, command.buyerUserId(), inquiry.getId()));

    // 주문서 제출까지 성공한 후 판매자 상담을 접수대기 상태로 복구
    inquiry.reopenSellerOnSubmission();

    // 타임라인 아이템 발행
    timelineItemPublisher.publishOrderFormSubmission(
        inquiry.getId(), command.buyerUserId(), submission.getId());

    // 사전 제출데이터 삭제
    orderFormDraftStorePort.delete(command.draftKey());

    return OrderFormDraftConsumeResult.from(inquiry, submission);
  }

  private CreateOrderFormSubmissionCommand toCreateSubmissionCommand(
      OrderFormDraftData draft, UUID buyerUserId, UUID inquiryId) {
    return new CreateOrderFormSubmissionCommand(
        draft.storeId(),
        buyerUserId,
        inquiryId,
        draft.orderFormTemplateId(),
        draft.formAnswers().stream()
            .map(answer -> new CreateOrderFormSubmissionCommand.FormAnswer(
                answer.optionGroupId(), answer.value()))
            .toList(),
        new CreateOrderFormSubmissionCommand.PickupRequest(draft.pickupDate(), draft.pickupTime()),
        new CreateOrderFormSubmissionCommand.NoticeAgreement(draft.noticeAgreed()),
        new CreateOrderFormSubmissionCommand.CancellationRefundAgreement(
            draft.cancellationRefundAgreed()),
        toSubmissionReferenceAssets(draft),
        !draft.startReferenceAssetProvided());
  }

  private static List<CreateOrderFormSubmissionCommand.ReferenceAsset> toSubmissionReferenceAssets(
      OrderFormDraftData draft) {
    if (draft.startReferenceAsset() == null) {
      return List.of();
    }
    return List.of(new CreateOrderFormSubmissionCommand.ReferenceAsset(
        draft.startReferenceAsset().assetId(), draft.startReferenceAsset().source(), 0));
  }

  private void validateStartReferenceAssets(OrderFormDraftData draft, UUID buyerUserId) {
    if (!draft.startReferenceAssetProvided() || draft.startReferenceAsset() == null) {
      return;
    }

    orderFormReferenceAssetValidator.validate(
        draft.storeId(), toSubmissionReferenceAssets(draft), buyerUserId);
  }
}
