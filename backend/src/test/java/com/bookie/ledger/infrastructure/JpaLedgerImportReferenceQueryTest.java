package com.bookie.ledger.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.ledger.domain.TransactionImportReference;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaLedgerImportReferenceQueryTest {

  @Mock private TransactionImportReferenceRepository importReferenceRepository;

  @InjectMocks private JpaLedgerImportReferenceQuery query;

  @Nested
  class FindSavedParentExternalIds {

    @Test
    void returnsEmptySetWithoutQueryingWhenNoIdsAreRequested() {
      Set<String> saved = query.findSavedParentExternalIds("OUTLOOK_EMAIL", List.of());

      assertThat(saved).isEmpty();
      verifyNoInteractions(importReferenceRepository);
    }

    @Test
    void returnsEmptySetWithoutQueryingWhenIdsAreNull() {
      Set<String> saved = query.findSavedParentExternalIds("OUTLOOK_EMAIL", null);

      assertThat(saved).isEmpty();
      verifyNoInteractions(importReferenceRepository);
    }

    @Test
    void collapsesAttachmentReferencesToTheirDistinctParents() {
      List<String> requested = List.of("message-1", "message-2", "message-3");
      when(importReferenceRepository.findByOriginAndParentExternalIdInAndTransactionDeletedAtIsNull(
              "OUTLOOK_EMAIL", requested))
          .thenReturn(
              List.of(reference("message-1"), reference("message-1"), reference("message-2")));

      Set<String> saved = query.findSavedParentExternalIds("OUTLOOK_EMAIL", requested);

      assertThat(saved).containsExactlyInAnyOrder("message-1", "message-2");
    }
  }

  private TransactionImportReference reference(String parentExternalId) {
    return TransactionImportReference.builder()
        .origin("OUTLOOK_EMAIL")
        .parentExternalId(parentExternalId)
        .build();
  }
}
