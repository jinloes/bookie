package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ParseSessionContextTest {

  private ParseSessionContext context;

  @BeforeEach
  void setUp() {
    context = new ParseSessionContext();
    context.clear();
  }

  @AfterEach
  void tearDown() {
    context.clear();
  }

  @Nested
  class AddUnrecognizedAlias {

    @Test
    void storesSingleAlias() {
      context.addUnrecognizedAlias("ACWD");
      assertThat(context.getUnrecognizedAliases()).containsExactly("ACWD");
    }

    @Test
    void storesMultipleAliasesInOrder() {
      context.addUnrecognizedAlias("ACWD");
      context.addUnrecognizedAlias("PG&E");
      assertThat(context.getUnrecognizedAliases()).containsExactly("ACWD", "PG&E");
    }
  }

  @Nested
  class GetUnrecognizedAliases {

    @Test
    void returnsEmptyWhenNoneAdded() {
      assertThat(context.getUnrecognizedAliases()).isEmpty();
    }

    @Test
    void returnsImmutableCopy() {
      context.addUnrecognizedAlias("ACWD");
      List<String> aliases = context.getUnrecognizedAliases();
      assertThatThrownBy(() -> aliases.add("extra"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void copyDoesNotReflectSubsequentAdds() {
      context.addUnrecognizedAlias("ACWD");
      List<String> snapshot = context.getUnrecognizedAliases();
      context.addUnrecognizedAlias("PG&E");
      assertThat(snapshot).containsExactly("ACWD");
    }
  }

  @Nested
  class Clear {

    @Test
    void removesAllAliases() {
      context.addUnrecognizedAlias("ACWD");
      context.clear();
      assertThat(context.getUnrecognizedAliases()).isEmpty();
    }

    @Test
    void isIdempotentOnEmptyState() {
      context.clear();
      context.clear();
      assertThat(context.getUnrecognizedAliases()).isEmpty();
    }
  }

  @Nested
  class ThreadLocalIsolation {

    @Test
    void isolatesAliasesPerThread() throws InterruptedException {
      context.addUnrecognizedAlias("main-thread");

      List<String> otherResult = new ArrayList<>();
      Thread other =
          new Thread(
              () -> {
                context.addUnrecognizedAlias("other-thread");
                otherResult.addAll(context.getUnrecognizedAliases());
                context.clear();
              });
      other.start();
      other.join();

      assertThat(context.getUnrecognizedAliases()).containsExactly("main-thread");
      assertThat(otherResult).containsExactly("other-thread");
    }
  }
}
