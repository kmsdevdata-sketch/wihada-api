package io.point3.p3api.notification.application.email;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.point3.p3api.mail.application.MailSenderPort;
import io.point3.p3api.mail.application.SendMailCommand;
import io.point3.p3api.mail.config.MailProperties;
import io.point3.p3api.notification.application.port.SellerEmailNotificationLogPort;
import io.point3.p3api.notification.domain.entity.SellerEmailNotificationLog;
import io.point3.p3api.notification.domain.type.EmailNotificationType;
import io.point3.p3api.user.application.port.UserPersistencePort;
import io.point3.p3api.user.domain.entity.User;
import io.point3.p3api.user.domain.type.SignupProvider;
import io.point3.p3api.user.domain.type.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderFormWaitingEmailServiceTest {

  private static final UUID SELLER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID INQUIRY_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-14T06:00:00Z"), ZoneOffset.UTC);

  @Test
  void sendsOncePerInquiry() {
    MailProperties properties = new MailProperties(
        true, "p3.cloud.project@gmail.com", "https://seller.wihada.com/seller/home?tab=waiting");
    RecordingMailSender mailSender = new RecordingMailSender();
    InMemoryEmailLogPort emailLogPort = new InMemoryEmailLogPort();

    OrderFormWaitingEmailService service = new OrderFormWaitingEmailService(
        properties, new FixedUserPort(seller("seller@example.com")), emailLogPort, mailSender, CLOCK);

    service.send(new OrderFormWaitingEmailEvent(SELLER_ID, INQUIRY_ID));
    service.send(new OrderFormWaitingEmailEvent(SELLER_ID, INQUIRY_ID));

    assertEquals(1, mailSender.commands.size());
    assertEquals(1, emailLogPort.keys.size());
    SendMailCommand command = mailSender.commands.getFirst();
    assertEquals("p3.cloud.project@gmail.com", command.from());
    assertEquals("seller@example.com", command.to());
    assertTrue(command.htmlBody().contains("문의대기 확인하기"));
    assertTrue(command.htmlBody().contains("https://seller.wihada.com/seller/home?tab=waiting"));
  }

  @Test
  void skipsWhenDisabled() {
    MailProperties properties = new MailProperties(
        false, "p3.cloud.project@gmail.com", "https://seller.wihada.com/seller/home?tab=waiting");
    RecordingMailSender mailSender = new RecordingMailSender();
    InMemoryEmailLogPort emailLogPort = new InMemoryEmailLogPort();

    OrderFormWaitingEmailService service = new OrderFormWaitingEmailService(
        properties, new FixedUserPort(seller("seller@example.com")), emailLogPort, mailSender, CLOCK);

    service.send(new OrderFormWaitingEmailEvent(SELLER_ID, INQUIRY_ID));

    assertTrue(mailSender.commands.isEmpty());
    assertTrue(emailLogPort.keys.isEmpty());
  }

  @Test
  void ignoresMailFailure() {
    MailProperties properties = new MailProperties(
        true, "p3.cloud.project@gmail.com", "https://seller.wihada.com/seller/home?tab=waiting");
    InMemoryEmailLogPort emailLogPort = new InMemoryEmailLogPort();

    OrderFormWaitingEmailService service = new OrderFormWaitingEmailService(
        properties,
        new FixedUserPort(seller("seller@example.com")),
        emailLogPort,
        command -> {
          throw new IllegalStateException("smtp error");
        },
        CLOCK);

    assertDoesNotThrow(() -> service.send(new OrderFormWaitingEmailEvent(SELLER_ID, INQUIRY_ID)));
    assertTrue(emailLogPort.keys.isEmpty());
  }

  private static User seller(String email) {
    return User.create(
        "seller-cognito-sub", email, "seller", UserRole.SELLER, "010-0000-0000", SignupProvider.KAKAO);
  }

  private record FixedUserPort(User user) implements UserPersistencePort {

    @Override
    public User save(User user) {
      return user;
    }

    @Override
    public Optional<User> findById(UUID userId) {
      return Optional.of(user);
    }

    @Override
    public List<User> findAllById(List<UUID> userIds) {
      return List.of(user);
    }

    @Override
    public Optional<User> findByEmail(String email) {
      return Optional.of(user);
    }
  }

  private static class InMemoryEmailLogPort implements SellerEmailNotificationLogPort {

    private final Set<String> keys = new HashSet<>();

    @Override
    public boolean existsByInquiryIdAndType(UUID inquiryId, EmailNotificationType type) {
      return keys.contains(key(inquiryId, type));
    }

    @Override
    public SellerEmailNotificationLog save(SellerEmailNotificationLog log) {
      keys.add(key(log.getInquiryId(), log.getType()));
      return log;
    }

    private String key(UUID inquiryId, EmailNotificationType type) {
      return inquiryId + ":" + type.name();
    }
  }

  private static class RecordingMailSender implements MailSenderPort {

    private final List<SendMailCommand> commands = new ArrayList<>();

    @Override
    public void sendHtml(SendMailCommand command) {
      commands.add(command);
    }
  }
}
