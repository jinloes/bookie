package com.bookie.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZonedDateTime;

@AnalyzeClasses(packages = "com.bookie", importOptions = ImportOption.DoNotIncludeTests.class)
class ClockUsageTest {

  private static final DescribedPredicate<JavaClass> OUTSIDE_CLOCK_ENTRY_POINTS =
      new DescribedPredicate<>("outside the application clock entry points") {
        @Override
        public boolean test(JavaClass javaClass) {
          return !javaClass.getName().equals("com.bookie.config.ClockConfiguration")
              && !javaClass.getName().equals("com.bookie.BookieApplication");
        }
      };

  @ArchTest
  static final ArchRule PRODUCTION_TIME_READS_USE_AN_INJECTED_CLOCK =
      noClasses()
          .should()
          .callMethod(LocalDate.class, "now")
          .orShould()
          .callMethod(LocalDateTime.class, "now")
          .orShould()
          .callMethod(LocalTime.class, "now")
          .orShould()
          .callMethod(Instant.class, "now")
          .orShould()
          .callMethod(Year.class, "now")
          .orShould()
          .callMethod(YearMonth.class, "now")
          .orShould()
          .callMethod(OffsetDateTime.class, "now")
          .orShould()
          .callMethod(ZonedDateTime.class, "now")
          .because("tests must be able to fix time through an injected Clock");

  @ArchTest
  static final ArchRule SYSTEM_CLOCK_CREATION_IS_RESTRICTED_TO_ENTRY_POINTS =
      noClasses()
          .that(OUTSIDE_CLOCK_ENTRY_POINTS)
          .should()
          .callMethod(Clock.class, "systemDefaultZone")
          .because("tests must be able to fix time through an injected Clock");
}
