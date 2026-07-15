package io.rosecloud.iam.api;

import io.rosecloud.iam.bootstrap.RosecloudIamProperties;
import io.rosecloud.iam.operator.OperatorSetupService;
import io.rosecloud.iam.session.OperatorLoginResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operator")
class OperatorController {

  private final OperatorSetupService operatorSetupService;
  private final RosecloudIamProperties properties;

  OperatorController(OperatorSetupService operatorSetupService, RosecloudIamProperties properties) {
    this.operatorSetupService = operatorSetupService;
    this.properties = properties;
  }

  @PostMapping("/setup/begin")
  ResponseEntity<OperatorSetupBeginResponse> beginSetup(
      @Valid @RequestBody OperatorSetupBeginRequest request) {
    OperatorSetupService.SetupBeginResult result =
        operatorSetupService.beginSetup(request.setupToken(), request.password());
    return ResponseEntity.ok(new OperatorSetupBeginResponse(result.totpSecret(), result.otpauthUrl()));
  }

  @PostMapping("/setup/complete")
  ResponseEntity<Void> completeSetup(@Valid @RequestBody OperatorSetupCompleteRequest request) {
    operatorSetupService.completeSetup(request.setupToken(), request.totpCode());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/login")
  ResponseEntity<OperatorLoginResponse> login(@Valid @RequestBody OperatorLoginRequest request) {
    OperatorLoginResult result = operatorSetupService.login(request.password(), request.totpCode());
    ResponseCookie refreshCookie =
        ResponseCookie.from("rc_refresh", result.refreshToken())
            .httpOnly(true)
            .secure(properties.cookies().secure())
            .sameSite("Lax")
            .path("/api")
            .maxAge(60L * 60 * 24 * 30)
            .build();

    return ResponseEntity.status(HttpStatus.OK)
        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
        .body(new OperatorLoginResponse(result.accessToken(), "Bearer", result.expiresInSeconds()));
  }
}
