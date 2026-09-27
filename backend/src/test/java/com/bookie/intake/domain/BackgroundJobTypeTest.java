package com.bookie.intake.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BackgroundJobTypeTest {

  @Nested
  class RunsAfterDismissal {
    @ParameterizedTest
    @EnumSource(BackgroundJobType.class)
    void isTrueOnlyForTheOutlookTranslationAndMove(BackgroundJobType type) {
      assertThat(type.runsAfterDismissal())
          .isEqualTo(
              Set.of(BackgroundJobType.TRANSLATE_OUTLOOK_ID, BackgroundJobType.MOVE_OUTLOOK)
                  .contains(type));
    }
  }
}
