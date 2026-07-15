package io.rosecloud.iam.api;

import io.rosecloud.iam.bootstrap.JwtIssuer;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class JwksController {

  private final JwtIssuer jwtIssuer;

  JwksController(JwtIssuer jwtIssuer) {
    this.jwtIssuer = jwtIssuer;
  }

  @GetMapping("/api/.well-known/jwks.json")
  Map<String, Object> jwks() {
    return jwtIssuer.jwks();
  }
}
