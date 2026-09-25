package com.bookie.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Nested
  class Lifecycle {

    @Test
    void returnsTheSameExecutorOnEveryCall() {
      AsyncConfig config = new AsyncConfig();
      try {
        assertThat(config.getAsyncExecutor()).isSameAs(config.getAsyncExecutor());
      } finally {
        config.destroy();
      }
    }

    @Test
    void closingTheContextShutsTheExecutorDown() {
      ThreadPoolTaskExecutor executor;
      try (AnnotationConfigApplicationContext context =
          new AnnotationConfigApplicationContext(AsyncConfig.class)) {
        executor = context.getBean(AsyncConfig.class).executor();
        executor.execute(() -> {});
        assertThat(executor.getThreadPoolExecutor().isShutdown()).isFalse();
      }

      assertThat(executor.getThreadPoolExecutor().isShutdown()).isTrue();
    }

    @Test
    void destroyWaitsForRunningTasksToFinish() throws Exception {
      AsyncConfig config = new AsyncConfig();
      CountDownLatch started = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      AtomicBoolean finished = new AtomicBoolean();
      config
          .getAsyncExecutor()
          .execute(
              () -> {
                started.countDown();
                awaitQuietly(release);
                finished.set(true);
              });
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

      CompletableFuture<Void> destroying = CompletableFuture.runAsync(config::destroy);
      await()
          .atMost(Duration.ofSeconds(5))
          .until(() -> config.executor().getThreadPoolExecutor().isShutdown());
      assertThat(destroying).isNotDone();
      assertThat(finished).isFalse();

      release.countDown();
      destroying.get(5, TimeUnit.SECONDS);

      assertThat(finished).isTrue();
      assertThat(config.executor().getThreadPoolExecutor().isTerminated()).isTrue();
    }

    @Test
    void springBootKeepsItsApplicationTaskExecutorForMvcAsync() {
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
          .withUserConfiguration(AsyncConfig.class)
          .run(context -> assertThat(context).hasBean("applicationTaskExecutor"));
    }
  }

  @Nested
  class MdcPropagation {

    @Test
    void runsTheTaskWithTheSubmittersMdc() throws Exception {
      AsyncConfig config = new AsyncConfig();
      try {
        MDC.put("requestId", "req-1");

        Map<String, String> seen = runOnSingleWorker(config, MDC::getCopyOfContextMap);

        assertThat(seen).containsExactly(Map.entry("requestId", "req-1"));
      } finally {
        config.destroy();
      }
    }

    @Test
    void doesNotLeakOneTasksMdcIntoTheNextOnTheSameWorker() throws Exception {
      AsyncConfig config = new AsyncConfig();
      try {
        MDC.put("requestId", "req-1");
        runOnSingleWorker(config, MDC::getCopyOfContextMap);
        MDC.clear();

        Map<String, String> seen = runOnSingleWorker(config, MDC::getCopyOfContextMap);

        assertThat(seen).isNullOrEmpty();
      } finally {
        config.destroy();
      }
    }
  }

  // A single-thread pool makes consecutive tasks share one worker, so leakage is observable.
  private static <T> T runOnSingleWorker(AsyncConfig config, Supplier<T> task) throws Exception {
    config.executor().setCorePoolSize(1);
    config.executor().setMaxPoolSize(1);
    CompletableFuture<T> result = new CompletableFuture<>();
    config.getAsyncExecutor().execute(() -> result.complete(task.get()));
    return result.get(5, TimeUnit.SECONDS);
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
