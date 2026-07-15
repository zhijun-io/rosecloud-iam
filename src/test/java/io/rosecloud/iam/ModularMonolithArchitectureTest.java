package io.rosecloud.iam;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "io.rosecloud.iam",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ModularMonolithArchitectureTest {

  @ArchTest
  static final ArchRule modules_are_free_of_cycles =
      slices().matching("io.rosecloud.iam.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule domain_modules_must_not_depend_on_api =
      noClasses()
          .that()
          .resideInAnyPackage(
              "io.rosecloud.iam.identity..",
              "io.rosecloud.iam.operator..",
              "io.rosecloud.iam.tenancy..",
              "io.rosecloud.iam.access..",
              "io.rosecloud.iam.session..",
              "io.rosecloud.iam.audit..",
              "io.rosecloud.iam.delivery..",
              "io.rosecloud.iam.shared..",
              "io.rosecloud.iam.bootstrap..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("io.rosecloud.iam.api..");

  @ArchTest
  static final ArchRule tenancy_must_not_depend_on_session_persistence =
      noClasses()
          .that()
          .resideInAPackage("io.rosecloud.iam.tenancy..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "io.rosecloud.iam.session.persistence..",
              "io.rosecloud.iam.session.infra..");
}
