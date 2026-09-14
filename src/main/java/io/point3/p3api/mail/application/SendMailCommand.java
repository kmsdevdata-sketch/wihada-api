package io.point3.p3api.mail.application;

import java.util.Objects;

public record SendMailCommand(String from, String to, String subject, String htmlBody) {

  public SendMailCommand {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(htmlBody, "htmlBody");

    if (from.isBlank() || to.isBlank() || subject.isBlank() || htmlBody.isBlank()) {
      throw new IllegalArgumentException("mail command values must not be blank");
    }
  }

  public static SendMailCommand of(String from, String to, String subject, String htmlBody) {
    return new SendMailCommand(from, to, subject, htmlBody);
  }
}
