package io.point3.p3api.mail.application;

public interface MailSenderPort {
  void sendHtml(SendMailCommand command);
}
