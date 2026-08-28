package com.bookie.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

@AnalyzeClasses(packages = "com.bookie", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

  private static final String[] CAPABILITY_PACKAGES = {
    "com.bookie.catalog..",
    "com.bookie.ledger..",
    "com.bookie.intake..",
    "com.bookie.reporting..",
    "com.bookie.integrations..",
    "com.bookie.datalifecycle.."
  };

  private static final String[] MODULE_CORE_PACKAGES = {
    "com.bookie.catalog..api..",
    "com.bookie.catalog..application..",
    "com.bookie.catalog..domain..",
    "com.bookie.ledger.api..",
    "com.bookie.ledger.application..",
    "com.bookie.ledger.domain..",
    "com.bookie.intake.api..",
    "com.bookie.intake.application..",
    "com.bookie.intake.domain..",
    "com.bookie.reporting.api..",
    "com.bookie.reporting.application..",
    "com.bookie.reporting.domain.."
  };

  private static final DescribedPredicate<JavaClass> LEGACY_JPA_ENTITY =
      new DescribedPredicate<>("a legacy JPA entity") {
        @Override
        public boolean test(JavaClass javaClass) {
          return javaClass.getPackageName().equals("com.bookie.model")
              && javaClass.isAnnotatedWith(Entity.class);
        }
      };

  @ArchTest
  static final ArchRule TARGET_MODULE_GRAPH_IS_DIRECTIONAL =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Catalog")
          .definedBy("com.bookie.catalog..")
          .layer("Ledger")
          .definedBy("com.bookie.ledger..")
          .layer("Intake")
          .definedBy("com.bookie.intake..")
          .layer("Reporting")
          .definedBy("com.bookie.reporting..")
          .layer("Integrations")
          .definedBy("com.bookie.integrations..")
          .layer("DataLifecycle")
          .definedBy("com.bookie.datalifecycle..")
          .whereLayer("Catalog")
          .mayOnlyBeAccessedByLayers("Ledger", "Intake", "Reporting")
          .whereLayer("Ledger")
          .mayOnlyBeAccessedByLayers("Intake", "Reporting")
          .whereLayer("Intake")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Reporting")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Integrations")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("DataLifecycle")
          .mayNotBeAccessedByAnyLayer();

  @ArchTest
  static final ArchRule CAPABILITIES_DO_NOT_USE_LEGACY_CONTROLLERS_OR_SERVICES =
      noClasses()
          .that()
          .resideInAnyPackage(CAPABILITY_PACKAGES)
          .and()
          .resideOutsideOfPackage("..compatibility..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.bookie.controller..", "com.bookie.service..");

  @ArchTest
  static final ArchRule CAPABILITIES_DO_NOT_USE_COMPOSITION_ADAPTERS =
      noClasses()
          .that()
          .resideInAnyPackage(CAPABILITY_PACKAGES)
          .and()
          .resideOutsideOfPackage("..compatibility..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.bookie.compatibility..");

  @ArchTest
  static final ArchRule MODULE_CORE_DOES_NOT_USE_LEGACY_REPOSITORIES =
      noClasses()
          .that()
          .resideInAnyPackage(MODULE_CORE_PACKAGES)
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.bookie.repository..");

  @ArchTest
  static final ArchRule CAPABILITY_LEGACY_REPOSITORY_ACCESS_IS_QUARANTINED =
      noClasses()
          .that()
          .resideInAnyPackage(CAPABILITY_PACKAGES)
          .and()
          .resideOutsideOfPackage("..compatibility..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.bookie.repository..");

  @ArchTest
  static final ArchRule MODULE_CORE_DOES_NOT_EXPOSE_LEGACY_ENTITIES =
      noClasses()
          .that()
          .resideInAnyPackage(MODULE_CORE_PACKAGES)
          .should()
          .dependOnClassesThat(LEGACY_JPA_ENTITY);

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
  static final ArchRule CATALOG_DOES_NOT_USE_LEGACY_CONTROLLERS_OR_SERVICES =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.catalog..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("com.bookie.controller..", "com.bookie.service..");

  @ArchTest
  static final ArchRule CATALOG_CORE_DOES_NOT_USE_LEGACY_REPOSITORIES =
      noClasses()
          .that()
          .resideInAnyPackage(
              "com.bookie.catalog.household..",
              "com.bookie.catalog.activity.api..",
              "com.bookie.catalog.activity.application..",
              "com.bookie.catalog.activity.domain..",
              "com.bookie.catalog.category.application..",
              "com.bookie.catalog.category.domain..",
              "com.bookie.catalog.classification.application..",
              "com.bookie.catalog.counterparty.api..",
              "com.bookie.catalog.counterparty.application..",
              "com.bookie.catalog.counterparty.domain..",
              "com.bookie.catalog.property.api..",
              "com.bookie.catalog.property.application..",
              "com.bookie.catalog.property.domain..",
              "com.bookie.catalog.reportpolicy.application..",
              "com.bookie.catalog.reportpolicy.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.bookie.repository..");

  @ArchTest
  static final ArchRule LEGACY_CODE_DOES_NOT_USE_CATALOG_INFRASTRUCTURE =
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
              "com.bookie.ledger.compatibility..",
              "com.bookie.ledger.infrastructure..");

  @ArchTest
  static final ArchRule LEDGER_APPLICATION_DOES_NOT_USE_ADAPTERS =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.ledger.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.bookie.ledger.api..",
              "com.bookie.ledger.compatibility..",
              "com.bookie.ledger.infrastructure..");

  @ArchTest
  static final ArchRule LEDGER_API_DOES_NOT_USE_INFRASTRUCTURE =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.ledger.api..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.bookie.ledger.compatibility..", "com.bookie.ledger.infrastructure..");

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
              "com.bookie.intake.compatibility..",
              "com.bookie.intake.infrastructure..");

  @ArchTest
  static final ArchRule INTAKE_APPLICATION_DOES_NOT_USE_ADAPTERS =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.intake.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.bookie.intake.api..",
              "com.bookie.intake.compatibility..",
              "com.bookie.intake.infrastructure..");

  @ArchTest
  static final ArchRule INTAKE_API_DOES_NOT_USE_INFRASTRUCTURE =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.intake.api..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.bookie.intake.compatibility..", "com.bookie.intake.infrastructure..");

  @ArchTest
  static final ArchRule REPORTING_DOES_NOT_USE_REPOSITORIES =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.reporting..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.bookie.repository..");

  @ArchTest
  static final ArchRule REPORTING_USES_ONLY_THE_LEDGER_APPLICATION_PORT =
      noClasses()
          .that()
          .resideInAPackage("com.bookie.reporting..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.bookie.ledger.compatibility..", "com.bookie.ledger.infrastructure..");

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
