package io.rosecloud.iam.session;

import org.springframework.http.HttpStatus;

public class SessionException extends RuntimeException {

  private final HttpStatus status;

  public SessionException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
