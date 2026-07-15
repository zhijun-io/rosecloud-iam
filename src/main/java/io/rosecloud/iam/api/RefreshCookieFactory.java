package io.rosecloud.iam.api;

import io.rosecloud.iam.bootstrap.RosecloudIamProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
class RefreshCookieFactory {

  private final RosecloudIamProperties properties;

  RefreshCookieFactory(RosecloudIamProperties properties) {
    this.properties = properties;
  }

  ResponseCookie issue(String refreshToken) {
    return ResponseCookie.from("rc_refresh", refreshToken)
        .httpOnly(true)
        .secure(properties.cookies().secure())
        .sameSite("Lax")
        .path("/api")
        .maxAge(properties.refreshTokenTtl())
        .build();
  }

  ResponseCookie clear() {
    return ResponseCookie.from("rc_refresh", "")
        .httpOnly(true)
        .secure(properties.cookies().secure())
        .sameSite("Lax")
        .path("/api")
        .maxAge(0)
        .build();
  }
}
