package io.rosecloud.iam.operator;

import io.rosecloud.iam.RosecloudIamApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class OperatorSetupCli {

  private OperatorSetupCli() {}

  public static void main(String[] args) {
    ConfigurableApplicationContext context =
        new SpringApplicationBuilder(RosecloudIamApplication.class)
            .web(WebApplicationType.NONE)
            .run(args);

    int exitCode = context.getBean(OperatorSetupCliRunner.class).run(System.out);
    int appExitCode = org.springframework.boot.SpringApplication.exit(context, () -> exitCode);
    System.exit(appExitCode);
  }
}
