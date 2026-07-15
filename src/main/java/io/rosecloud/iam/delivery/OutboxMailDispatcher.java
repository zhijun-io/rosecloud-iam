package io.rosecloud.iam.delivery;

import io.rosecloud.iam.bootstrap.RosecloudIamProperties;
import jakarta.mail.internet.MimeMessage;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "rosecloud.iam.mail", name = "enabled", havingValue = "true")
public class OutboxMailDispatcher {

  private static final Logger log = LoggerFactory.getLogger(OutboxMailDispatcher.class);

  private final OutboxMessageRepository outboxMessageRepository;
  private final JavaMailSender mailSender;
  private final RosecloudIamProperties properties;
  private final Clock clock = Clock.systemUTC();

  public OutboxMailDispatcher(
      OutboxMessageRepository outboxMessageRepository,
      JavaMailSender mailSender,
      RosecloudIamProperties properties) {
    this.outboxMessageRepository = outboxMessageRepository;
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @Scheduled(fixedDelay = 2000)
  @Transactional
  public void dispatchPending() {
    for (OutboxMessage message :
        outboxMessageRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()) {
      try {
        if (isInvitationEvent(message.eventType())) {
          sendInvitation(message);
        }
        message.markPublished(Instant.now(clock));
      } catch (Exception exception) {
        log.warn(
            "Failed to publish outbox message {} ({}), will retry",
            message.id(),
            message.eventType(),
            exception);
      }
    }
  }

  private static boolean isInvitationEvent(String eventType) {
    return "tenant.owner_invited".equals(eventType) || "tenant.member_invited".equals(eventType);
  }

  private void sendInvitation(OutboxMessage message) throws Exception {
    String email = extractJsonField(message.payload(), "email");
    String token = extractJsonField(message.payload(), "token");
    String roleCode = extractJsonField(message.payload(), "roleCode");
    if (email.isBlank() || token.isBlank()) {
      throw new IllegalStateException("invitation payload missing email/token");
    }

    String acceptUrl = properties.mail().inviteBaseUrl().replaceAll("/$", "") + "/#/accept-invite";
    String subject =
        "tenant.owner_invited".equals(message.eventType())
            ? "RoseCloud IAM owner invitation"
            : "RoseCloud IAM member invitation (" + roleCode + ")";
    String body =
        """
        You have been invited to RoseCloud IAM.

        Role: %s
        Open the console accept page:
        %s

        Paste this one-time invitation token:
        %s

        (Local Mailpit capture — development only.)
        """
            .formatted(roleCode.isBlank() ? "OWNER" : roleCode, acceptUrl, token);

    MimeMessage mimeMessage = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
    helper.setFrom(properties.mail().from());
    helper.setTo(email);
    helper.setSubject(subject);
    helper.setText(body, false);
    mailSender.send(mimeMessage);
  }

  static String extractJsonField(String payload, String fieldName) {
    String marker = "\"" + fieldName + "\":\"";
    int start = payload.indexOf(marker);
    if (start < 0) {
      return "";
    }
    int valueStart = start + marker.length();
    int valueEnd = payload.indexOf('"', valueStart);
    if (valueEnd < 0) {
      return "";
    }
    return payload.substring(valueStart, valueEnd);
  }
}
