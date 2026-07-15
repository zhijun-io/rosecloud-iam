package io.rosecloud.iam.bootstrap;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rosecloud.iam")
public record RosecloudIamProperties(
    Cookies cookies,
    Crypto crypto,
    Jwt jwt,
    Duration setupTokenTtl,
    Duration invitationTokenTtl,
    Duration refreshReuseGrace,
    Duration refreshTokenTtl,
    int maxSessionsPerUser,
    LoginRateLimit loginRateLimit) {

  public record Cookies(boolean secure) {}

  public record Crypto(String totpKeyId, String totpKey) {}

  public record Jwt(Duration accessTokenTtl) {}

  public record LoginRateLimit(
      int maxFailuresBeforeCooldown, Duration initialCooldown, Duration maxCooldown) {}
}
