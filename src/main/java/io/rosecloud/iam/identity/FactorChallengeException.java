package io.rosecloud.iam.identity;

import org.springframework.http.HttpStatus;

public class FactorChallengeException extends RuntimeException {

  private final HttpStatus status;

  public FactorChallengeException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
