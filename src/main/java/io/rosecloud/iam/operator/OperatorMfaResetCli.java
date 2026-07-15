package io.rosecloud.iam.operator;

import io.rosecloud.iam.RosecloudIamApplication;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Server-local disaster recovery: reset a PlatformOperator's FactorBinding + RecoveryCode and revoke
 * sessions. Not exposed over HTTP.
 */
public final class OperatorMfaResetCli {

  private OperatorMfaResetCli() {}

  public static void main(String[] args) {
    ConfigurableApplicationContext context =
        new SpringApplicationBuilder(RosecloudIamApplication.class)
            .web(WebApplicationType.NONE)
            .run(args);

    int exitCode =
        context
            .getBean(OperatorMfaResetCliRunner.class)
            .run(context.getBean(ApplicationArguments.class), System.out);
    int appExitCode = org.springframework.boot.SpringApplication.exit(context, () -> exitCode);
    System.exit(appExitCode);
  }
}
