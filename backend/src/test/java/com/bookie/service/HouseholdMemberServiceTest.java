package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.HouseholdMember;
import com.bookie.model.UpsertHouseholdMemberRequest;
import com.bookie.repository.HouseholdMemberRepository;
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

  @Mock private HouseholdMemberRepository householdMemberRepository;

  @InjectMocks private HouseholdMemberService householdMemberService;

  @Nested
  class Create {

    @Test
    void defaultsNewMemberToActive() {
      when(householdMemberRepository.findByNameIgnoreCase("Alex")).thenReturn(Optional.empty());
      when(householdMemberRepository.save(any()))
          .thenAnswer(invocation -> invocation.getArgument(0));
      ArgumentCaptor<HouseholdMember> captor = ArgumentCaptor.forClass(HouseholdMember.class);

      householdMemberService.create(new UpsertHouseholdMemberRequest(" Alex ", null));

      verify(householdMemberRepository).save(captor.capture());
      assertThat(captor.getValue().getName()).isEqualTo("Alex");
      assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void rejectsDuplicateName() {
      when(householdMemberRepository.findByNameIgnoreCase("Alex"))
          .thenReturn(Optional.of(HouseholdMember.builder().id(1L).name("Alex").build()));

      assertThatThrownBy(
              () -> householdMemberService.create(new UpsertHouseholdMemberRequest("Alex", true)))
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
              .systemKey(HouseholdMemberService.DEFAULT_HOUSEHOLD_KEY)
              .build();
      when(householdMemberRepository.findById(1L)).thenReturn(Optional.of(member));

      assertThatThrownBy(
              () ->
                  householdMemberService.update(
                      1L, new UpsertHouseholdMemberRequest("Renamed", true)))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("cannot be edited");
    }
  }
}
