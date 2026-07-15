package io.rosecloud.iam.bootstrap;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rosecloud.iam")
public record RosecloudIamProperties(
    Cookies cookies, Crypto crypto, Jwt jwt, Duration setupTokenTtl, Duration invitationTokenTtl) {

  public record Cookies(boolean secure) {}

  public record Crypto(String totpKeyId, String totpKey) {}

  public record Jwt(Duration accessTokenTtl) {}
}
