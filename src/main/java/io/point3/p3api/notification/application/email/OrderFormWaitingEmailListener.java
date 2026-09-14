package io.point3.p3api.notification.application.email;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 주문서 저장 커밋 이후 판매자 메일 발송 */
@Component
@RequiredArgsConstructor
public class OrderFormWaitingEmailListener {

  private final OrderFormWaitingEmailService orderFormWaitingEmailService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void send(OrderFormWaitingEmailEvent event) {
    orderFormWaitingEmailService.send(event);
  }
}
