package com.bookie.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.model.ParsedEmailKeywords;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
class ParsedEmailKeywordsRepositoryTest {

  @Autowired private TestEntityManager em;
  @Autowired private ParsedEmailKeywordsRepository repo;

  @Nested
  class DeleteBySourceId {

    @Test
    void removesAllRowsForThatSource() {
      em.persist(ParsedEmailKeywords.builder().sourceId("msg-1").keyword("a").build());
      em.persist(ParsedEmailKeywords.builder().sourceId("msg-1").keyword("b").build());
      em.persist(ParsedEmailKeywords.builder().sourceId("msg-2").keyword("c").build());
      em.flush();
      em.clear();

      int deleted = repo.deleteBySourceId("msg-1");

      assertThat(deleted).isEqualTo(2);
      assertThat(repo.findBySourceId("msg-1")).isEmpty();
      assertThat(repo.findBySourceId("msg-2")).hasSize(1);
    }

    // Regression test: derived deleteBySourceId left old rows in the action queue, so Hibernate
    // flushed the saveAll inserts first and tripped the unique constraint on (source_id, keyword).
    // The @Modifying bulk DELETE executes immediately, so the inserts of the same keys then
    // succeed.
    @Test
    @Transactional
    void allowsReinsertingSameKeysInSameTransaction() {
      em.persist(ParsedEmailKeywords.builder().sourceId("msg-1").keyword("acct-123").build());
      em.persist(ParsedEmailKeywords.builder().sourceId("msg-1").keyword("invoice-9").build());
      em.flush();
      em.clear();

      repo.deleteBySourceId("msg-1");
      repo.saveAll(
          List.of(
              ParsedEmailKeywords.builder().sourceId("msg-1").keyword("acct-123").build(),
              ParsedEmailKeywords.builder().sourceId("msg-1").keyword("invoice-9").build(),
              ParsedEmailKeywords.builder().sourceId("msg-1").keyword("new-kw").build()));
      em.flush();

      List<ParsedEmailKeywords> rows = repo.findBySourceId("msg-1");
      assertThat(rows)
          .extracting(ParsedEmailKeywords::getKeyword)
          .containsExactlyInAnyOrder("acct-123", "invoice-9", "new-kw");
    }

    @Test
    void zeroWhenNoMatchingRows() {
      int deleted = repo.deleteBySourceId("does-not-exist");
      assertThat(deleted).isZero();
    }
  }
}
