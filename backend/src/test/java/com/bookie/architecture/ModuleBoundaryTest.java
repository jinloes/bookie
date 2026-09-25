package com.bookie.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.bookie.BookieApplication;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

@AnalyzeClasses(packages = "com.bookie", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

  // Each top-level package is an application module that may use only the modules and named
  // interfaces its package-info declares in allowedDependencies; verify() also rejects module
  // cycles.
  @Test
  void applicationModulesUseOnlyTheirDeclaredDependencies() {
    ApplicationModules.of(BookieApplication.class).verify();
  }

  @ArchTest
  static final ArchRule LEDGER_INFRASTRUCTURE_IS_INTERNAL = infrastructureIsInternalTo("ledger");

  @ArchTest
  static final ArchRule INTAKE_INFRASTRUCTURE_IS_INTERNAL = infrastructureIsInternalTo("intake");

  @ArchTest
  static final ArchRule REPORTING_INFRASTRUCTURE_IS_INTERNAL =
      infrastructureIsInternalTo("reporting");

  @ArchTest
  static final ArchRule CATALOG_MODULES_ARE_FREE_OF_CYCLES =
      slices().matching("com.bookie.catalog.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule LEDGER_LAYERS_ARE_FREE_OF_CYCLES =
      slices().matching("com.bookie.ledger.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule INTAKE_LAYERS_ARE_FREE_OF_CYCLES =
      slices().matching("com.bookie.intake.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule INTEGRATION_ADAPTERS_ARE_FREE_OF_CYCLES =
      slices().matching("com.bookie.integrations.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule REPORTING_LAYERS_ARE_FREE_OF_CYCLES =
      slices().matching("com.bookie.reporting.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule PROVIDER_SDKS_ARE_CONFINED_TO_INTEGRATIONS =
      noClasses()
          .that()
          .resideOutsideOfPackage("com.bookie.integrations..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.microsoft..",
              "com.github.copilot..",
              "org.springframework.ai..",
              "org.apache.pdfbox..",
              "org.apache.commons.csv..");

  @ArchTest
  static final ArchRule CATALOG_INFRASTRUCTURE_IS_INTERNAL =
      noClasses()
          .that()
          .resideOutsideOfPackage("com.bookie.catalog..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.bookie.catalog..infrastructure..");

  @ArchTest
  static final ArchRule DOMAIN_DOES_NOT_USE_OUTER_LAYERS =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.catalog..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.bookie.catalog..api..",
              "com.bookie.catalog..application..",
              "com.bookie.catalog..infrastructure..");

  @ArchTest
  static final ArchRule APPLICATION_DOES_NOT_USE_API_OR_INFRASTRUCTURE =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.catalog..application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.bookie.catalog..api..", "com.bookie.catalog..infrastructure..");

  @ArchTest
  static final ArchRule API_DOES_NOT_USE_INFRASTRUCTURE =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.catalog..api..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.bookie.catalog..infrastructure..");

  @ArchTest
  static final ArchRule INFRASTRUCTURE_DOES_NOT_USE_API =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.catalog..infrastructure..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.bookie.catalog..api..");

  @ArchTest
  static final ArchRule LEDGER_DOMAIN_DOES_NOT_USE_OUTER_LAYERS =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.ledger.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.bookie.ledger.api..",
              "com.bookie.ledger.application..",
              "com.bookie.ledger.infrastructure..");

  @ArchTest
  static final ArchRule LEDGER_APPLICATION_DOES_NOT_USE_ADAPTERS =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.ledger.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.bookie.ledger.api..", "com.bookie.ledger.infrastructure..");

  @ArchTest
  static final ArchRule LEDGER_API_DOES_NOT_USE_INFRASTRUCTURE =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.ledger.api..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.bookie.ledger.infrastructure..");

  @ArchTest
  static final ArchRule INTAKE_DOMAIN_DOES_NOT_USE_OUTER_LAYERS =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.intake.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.bookie.intake.api..",
              "com.bookie.intake.application..",
              "com.bookie.intake.infrastructure..");

  @ArchTest
  static final ArchRule INTAKE_APPLICATION_DOES_NOT_USE_ADAPTERS =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.intake.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.bookie.intake.api..", "com.bookie.intake.infrastructure..");

  @ArchTest
  static final ArchRule INTAKE_API_DOES_NOT_USE_INFRASTRUCTURE =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.intake.api..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.bookie.intake.infrastructure..");

  @ArchTest
  static final ArchRule INTAKE_CORE_DOES_NOT_USE_INTAKE_FEATURES =
      noClasses()
          .that()
          .resideInAnyPackage("com.bookie.intake.application..", "com.bookie.intake.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.bookie.intake.extraction..", "com.bookie.intake.sources..");

  @ArchTest
  static final ArchRule INTAKE_EXTRACTION_DOES_NOT_USE_SOURCES =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.intake.extraction..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.bookie.intake.sources..");

  @ArchTest
  static final ArchRule REPORTING_USES_ONLY_THE_LEDGER_APPLICATION_PORT =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.reporting..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.bookie.ledger.infrastructure..");

  @ArchTest
  static final ArchRule REPORTING_DOMAIN_DOES_NOT_USE_OUTER_LAYERS =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.reporting.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.bookie.reporting.api..",
              "com.bookie.reporting.application..",
              "com.bookie.reporting.infrastructure..");

  @ArchTest
  static final ArchRule REPORTING_APPLICATION_DOES_NOT_USE_API_OR_INFRASTRUCTURE =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.reporting.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.bookie.reporting.api..", "com.bookie.reporting.infrastructure..");

  @ArchTest
  static final ArchRule REPORTING_API_DOES_NOT_USE_INFRASTRUCTURE =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.reporting.api..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.bookie.reporting.infrastructure..");

  private static ArchRule infrastructureIsInternalTo(String module) {
    String modulePackage = "com.bookie." + module + "..";
    String infrastructurePackage = "com.bookie." + module + ".infrastructure..";
    return noClasses()
        .that()
        .resideOutsideOfPackage(modulePackage)
        .should()
        .dependOnClassesThat()
        .resideInAPackage(infrastructurePackage);
  }
}
