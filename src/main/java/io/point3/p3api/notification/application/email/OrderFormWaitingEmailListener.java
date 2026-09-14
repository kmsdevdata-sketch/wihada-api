package io.point3.p3api.notification.application.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 주문서 저장 커밋 이후 판매자 메일 발송 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFormWaitingEmailListener {

  private final OrderFormWaitingEmailService orderFormWaitingEmailService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void send(OrderFormWaitingEmailEvent event) {
    log.info(
        "Handle order form waiting email event. submissionId={}, inquiryId={}, sellerUserId={}",
        event.submissionId(),
        event.inquiryId(),
        event.sellerUserId());
    orderFormWaitingEmailService.send(event);
  }
}
