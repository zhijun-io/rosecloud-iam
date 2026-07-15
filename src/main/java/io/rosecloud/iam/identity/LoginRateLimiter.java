package io.rosecloud.iam.identity;

import io.rosecloud.iam.bootstrap.RosecloudIamProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory progressive cooldown keyed by User login identifier (verified email) + client IP
 * (iam-v1 §5.4). Not a permanent lock — counters expire after the cooldown window.
 */
@Component
public class LoginRateLimiter {

  private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
  private final RosecloudIamProperties properties;
  private final Clock clock = Clock.systemUTC();

  public LoginRateLimiter(RosecloudIamProperties properties) {
    this.properties = properties;
  }

  public void assertAllowed(String email, String clientIp) {
    String key = key(email, clientIp);
    Entry entry = entries.get(key);
    if (entry == null) {
      return;
    }

    Instant now = clock.instant();
    if (entry.blockedUntil() != null && now.isBefore(entry.blockedUntil())) {
      throw new LoginRateLimitedException(Duration.between(now, entry.blockedUntil()));
    }
  }

  public void recordFailure(String email, String clientIp) {
    String key = key(email, clientIp);
    Instant now = clock.instant();
    entries.compute(
        key,
        (ignored, existing) -> {
          int failures = existing == null ? 1 : existing.failures() + 1;
          Instant blockedUntil = existing == null ? null : existing.blockedUntil();
          var limit = properties.loginRateLimit();
          if (failures >= limit.maxFailuresBeforeCooldown()) {
            int excess = failures - limit.maxFailuresBeforeCooldown();
            long factor = 1L << Math.min(Math.max(excess, 0), 10);
            Duration cooldown = limit.initialCooldown().multipliedBy(factor);
            if (cooldown.compareTo(limit.maxCooldown()) > 0) {
              cooldown = limit.maxCooldown();
            }
            blockedUntil = now.plus(cooldown);
          }
          return new Entry(failures, blockedUntil);
        });
  }

  public void recordSuccess(String email, String clientIp) {
    entries.remove(key(email, clientIp));
  }

  private static String key(String loginIdentifier, String clientIp) {
    String normalizedIdentifier =
        loginIdentifier == null ? "" : loginIdentifier.trim().toLowerCase(Locale.ROOT);
    String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim();
    return normalizedIdentifier + "|" + ip;
  }

  private record Entry(int failures, Instant blockedUntil) {}
}
