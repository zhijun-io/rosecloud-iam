package io.rosecloud.iam.operator;

import java.io.PrintStream;
import org.springframework.stereotype.Component;

@Component
public class OperatorSetupCliRunner {

  private final OperatorSetupTokenIssuer operatorSetupTokenIssuer;

  public OperatorSetupCliRunner(OperatorSetupTokenIssuer operatorSetupTokenIssuer) {
    this.operatorSetupTokenIssuer = operatorSetupTokenIssuer;
  }

  public int run(PrintStream out) {
    try {
      out.println(operatorSetupTokenIssuer.issue());
      return 0;
    } catch (OperatorSetupRejectedException exception) {
      return 1;
    }
  }
}
