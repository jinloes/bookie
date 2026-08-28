package com.bookie.catalog.household.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.catalog.household.domain.HouseholdMember;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class HouseholdMemberServiceTest {

  @Mock private HouseholdMemberStore householdMemberStore;

  @InjectMocks private HouseholdMemberService householdMemberService;

  @Nested
  class Create {

    @Test
    void defaultsNewMemberToActive() {
      when(householdMemberStore.findByNameIgnoreCase("Alex")).thenReturn(Optional.empty());
      when(householdMemberStore.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      ArgumentCaptor<HouseholdMember> captor = ArgumentCaptor.forClass(HouseholdMember.class);

      householdMemberService.create(new UpsertHouseholdMemberCommand(" Alex ", null));

      verify(householdMemberStore).save(captor.capture());
      assertThat(captor.getValue().getName()).isEqualTo("Alex");
      assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void rejectsDuplicateName() {
      when(householdMemberStore.findByNameIgnoreCase("Alex"))
          .thenReturn(Optional.of(HouseholdMember.builder().id(1L).name("Alex").build()));

      assertThatThrownBy(
              () -> householdMemberService.create(new UpsertHouseholdMemberCommand("Alex", true)))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("already exists");
    }
  }

  @Nested
  class Update {

    @Test
    void rejectsEditingSystemMember() {
      HouseholdMember member =
          HouseholdMember.builder()
              .id(1L)
              .name("Household")
              .systemKey(HouseholdCatalog.DEFAULT_HOUSEHOLD_KEY)
              .build();
      when(householdMemberStore.findById(1L)).thenReturn(Optional.of(member));

      assertThatThrownBy(
              () ->
                  householdMemberService.update(
                      1L, new UpsertHouseholdMemberCommand("Renamed", true)))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("cannot be edited");
    }
  }

  @Nested
  class GetDefaultHouseholdMember {

    @Test
    void returnsSystemMember() {
      HouseholdMember member =
          HouseholdMember.builder()
              .id(1L)
              .name("Household")
              .systemKey(HouseholdCatalog.DEFAULT_HOUSEHOLD_KEY)
              .build();
      when(householdMemberStore.findBySystemKey(HouseholdCatalog.DEFAULT_HOUSEHOLD_KEY))
          .thenReturn(Optional.of(member));

      assertThat(householdMemberService.getDefaultHouseholdMember()).isSameAs(member);
    }

    @Test
    void rejectsMissingSystemMember() {
      when(householdMemberStore.findBySystemKey(HouseholdCatalog.DEFAULT_HOUSEHOLD_KEY))
          .thenReturn(Optional.empty());

      assertThatThrownBy(householdMemberService::getDefaultHouseholdMember)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("default household member");
    }
  }
}
