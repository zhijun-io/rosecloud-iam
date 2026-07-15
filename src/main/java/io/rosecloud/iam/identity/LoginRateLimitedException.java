package io.rosecloud.iam.identity;

import java.time.Duration;

public class LoginRateLimitedException extends RuntimeException {

  private final Duration retryAfter;

  public LoginRateLimitedException(Duration retryAfter) {
    super("login temporarily limited");
    this.retryAfter = retryAfter;
  }

  public Duration retryAfter() {
    return retryAfter;
  }
}
