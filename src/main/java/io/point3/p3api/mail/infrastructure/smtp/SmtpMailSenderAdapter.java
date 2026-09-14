package io.point3.p3api.mail.infrastructure.smtp;

import io.point3.p3api.mail.application.MailSenderPort;
import io.point3.p3api.mail.application.SendMailCommand;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmtpMailSenderAdapter implements MailSenderPort {

  private final JavaMailSender javaMailSender;

  @Override
  public void sendHtml(SendMailCommand command) {
    MimeMessage message = javaMailSender.createMimeMessage();
    try {
      MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
      helper.setFrom(command.from());
      helper.setTo(command.to());
      helper.setSubject(command.subject());
      helper.setText(command.htmlBody(), true);
    } catch (MessagingException e) {
      throw new MailPreparationException("Failed to prepare mail message", e);
    }
    javaMailSender.send(message);
  }
}
