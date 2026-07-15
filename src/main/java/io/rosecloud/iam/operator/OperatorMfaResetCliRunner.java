package io.rosecloud.iam.operator;

import java.io.PrintStream;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

@Component
public class OperatorMfaResetCliRunner {

  private final OperatorFactorService operatorFactorService;

  public OperatorMfaResetCliRunner(OperatorFactorService operatorFactorService) {
    this.operatorFactorService = operatorFactorService;
  }

  public int run(ApplicationArguments arguments, PrintStream out) {
    List<String> args = arguments.getNonOptionArgs();
    if (args.size() < 2) {
      out.println("usage: OperatorMfaResetCli <operatorId> <reason>");
      return 2;
    }
    try {
      UUID operatorId = UUID.fromString(args.get(0));
      String reason = args.get(1).trim();
      if (reason.isBlank()) {
        out.println("reason required");
        return 2;
      }
      operatorFactorService.resetCredentials(operatorId, "cli: " + reason);
      out.println("operator mfa credentials reset: " + operatorId);
      return 0;
    } catch (IllegalArgumentException exception) {
      out.println("invalid operatorId");
      return 2;
    } catch (RuntimeException exception) {
      out.println(exception.getMessage());
      return 1;
    }
  }
}
