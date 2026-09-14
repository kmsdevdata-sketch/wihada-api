package io.point3.p3api.notification.application.email;

import io.point3.p3api.mail.application.MailSenderPort;
import io.point3.p3api.mail.application.SendMailCommand;
import io.point3.p3api.mail.config.MailProperties;
import io.point3.p3api.notification.application.port.SellerEmailNotificationLogPort;
import io.point3.p3api.notification.domain.entity.SellerEmailNotificationLog;
import io.point3.p3api.notification.domain.type.EmailNotificationType;
import io.point3.p3api.user.application.port.UserPersistencePort;
import io.point3.p3api.user.domain.entity.User;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OrderFormWaitingEmailService {

  private static final EmailNotificationType TYPE = EmailNotificationType.ORDER_FORM_WAITING;
  private static final String SUBJECT = "새로운 주문서가 접수되었습니다";

  private final MailProperties mailProperties;
  private final UserPersistencePort userPersistencePort;
  private final SellerEmailNotificationLogPort sellerEmailNotificationLogPort;
  private final MailSenderPort mailSenderPort;
  private final Clock clock;

  public void send(OrderFormWaitingEmailEvent event) {
    String notReadyReason = notReadyReason();
    if (notReadyReason != null) {
      log.warn(
          "Skip order form waiting email. reason={}, submissionId={}, inquiryId={}, sellerUserId={}",
          notReadyReason,
          event.submissionId(),
          event.inquiryId(),
          event.sellerUserId());
      return;
    }
    if (sellerEmailNotificationLogPort.existsByInquiryIdAndType(event.inquiryId(), TYPE)) {
      log.info(
          "Skip order form waiting email. reason=already_sent, submissionId={}, inquiryId={}, sellerUserId={}",
          event.submissionId(),
          event.inquiryId(),
          event.sellerUserId());
      return;
    }

    User seller = userPersistencePort.findById(event.sellerUserId()).orElse(null);
    if (seller == null || isBlank(seller.getEmail())) {
      log.warn(
          "Skip order form waiting email. reason={}, submissionId={}, inquiryId={}, sellerUserId={}",
          seller == null ? "seller_not_found" : "seller_email_blank",
          event.submissionId(),
          event.inquiryId(),
          event.sellerUserId());
      return;
    }

    try {
      log.info(
          "Send order form waiting email. submissionId={}, inquiryId={}, sellerUserId={}, recipient={}",
          event.submissionId(),
          event.inquiryId(),
          event.sellerUserId(),
          maskEmail(seller.getEmail()));
      mailSenderPort.sendHtml(
          SendMailCommand.of(mailProperties.from(), seller.getEmail(), SUBJECT, createHtmlBody()));
      sellerEmailNotificationLogPort.save(SellerEmailNotificationLog.create(
          event.sellerUserId(), event.inquiryId(), TYPE, clock.instant()));
      log.info(
          "Sent order form waiting email. submissionId={}, inquiryId={}, sellerUserId={}, recipient={}",
          event.submissionId(),
          event.inquiryId(),
          event.sellerUserId(),
          maskEmail(seller.getEmail()));
    } catch (RuntimeException e) {
      log.error(
          "Failed to send order form waiting email. submissionId={}, inquiryId={}, sellerUserId={}, recipient={}",
          event.submissionId(),
          event.inquiryId(),
          event.sellerUserId(),
          maskEmail(seller.getEmail()),
          e);
    }
  }

  private String createHtmlBody() {
    return """
        <!doctype html>
        <html lang="ko">
          <body style="margin:0;padding:0;background-color:#f5f6f8;">
            <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">
              새로운 주문서가 접수되었습니다. 판매자 페이지에서 확인해 주세요.
            </div>
            <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f5f6f8;">
              <tr>
                <td align="center" style="padding:40px 16px;">
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width:100%;max-width:560px;background-color:#ffffff;border-radius:16px;overflow:hidden;">
                    <tr>
                      <td style="padding:32px 32px 24px;font-family:Arial,'Apple SD Gothic Neo','Noto Sans KR',sans-serif;font-size:24px;line-height:32px;font-weight:700;color:#12161c;">
                        wihada
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:0 32px;font-family:Arial,'Apple SD Gothic Neo','Noto Sans KR',sans-serif;color:#12161c;">
                        <h2 style="margin:0;font-size:24px;line-height:34px;font-weight:700;letter-spacing:-0.5px;">
                          새로운 주문서가 접수되었어요
                        </h2>
                        <p style="margin:16px 0 0;font-size:16px;line-height:26px;font-weight:400;color:#6c6e72;">
                          구매자가 주문서를 작성해 보냈습니다.<br />
                          판매자 페이지의 문의 대기 목록에서 내용을 확인해 주세요.
                        </p>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:32px;">
                        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
                          <tr>
                            <td align="center" bgcolor="#12161c" style="border-radius:12px;">
                              <a href="{{sellerInquiriesUrl}}" target="_blank" style="display:block;padding:15px 20px;font-family:Arial,'Apple SD Gothic Neo','Noto Sans KR',sans-serif;font-size:16px;line-height:22px;font-weight:700;color:#ffffff;text-decoration:none;border-radius:12px;">
                                문의대기 확인하기
                              </a>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:24px 32px 32px;border-top:1px solid #eceef1;font-family:Arial,'Apple SD Gothic Neo','Noto Sans KR',sans-serif;font-size:13px;line-height:20px;color:#a3a5a9;">
                        본 메일은 새로운 주문서가 접수되어 자동으로 발송되었습니다.
                      </td>
                    </tr>
                  </table>
                </td>
              </tr>
            </table>
          </body>
        </html>
        """.replace("{{sellerInquiriesUrl}}", mailProperties.sellerInquiriesUrl());
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String notReadyReason() {
    if (!mailProperties.enabled()) {
      return "mail_disabled";
    }
    if (isBlank(mailProperties.from())) {
      return "mail_from_blank";
    }
    if (isBlank(mailProperties.sellerInquiriesUrl())) {
      return "seller_inquiries_url_blank";
    }
    return null;
  }

  private String maskEmail(String email) {
    int atIndex = email.indexOf('@');
    if (atIndex <= 1) {
      return "***";
    }
    return email.charAt(0) + "***" + email.substring(atIndex);
  }
}
