package com.bookie.catalog.reportpolicy.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.catalog.reportpolicy.application.ReportPolicyParityException;
import com.bookie.catalog.reportpolicy.domain.ActivityReportingProfileAssignment;
import com.bookie.catalog.reportpolicy.domain.ReportingProfile;
import com.bookie.catalog.reportpolicy.domain.ReportingProfileKey;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaReportingProfileAssignmentCatalogTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);

  @Mock private ReportingProfileRepository reportingProfileRepository;
  @Mock private ActivityReportingProfileAssignmentRepository assignmentRepository;

  private JpaReportingProfileAssignmentCatalog catalog;

  @BeforeEach
  void setUp() {
    catalog =
        new JpaReportingProfileAssignmentCatalog(
            reportingProfileRepository, assignmentRepository, CLOCK);
  }

  @Nested
  class SynchronizeCurrent {

    @Test
    void failsClosedWhenTheProfileSeedIsMissing() {
      when(reportingProfileRepository.findByKeyAndActiveTrue(ReportingProfileKey.W2))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> catalog.synchronizeCurrent(10L, ReportingProfileKey.W2))
          .isInstanceOf(ReportPolicyParityException.class)
          .hasMessageContaining("missing");
    }

    @Test
    void failsClosedWhenMultipleOpenAssignmentsExist() {
      ReportingProfile profile = profile(ReportingProfileKey.W2);
      when(reportingProfileRepository.findByKeyAndActiveTrue(ReportingProfileKey.W2))
          .thenReturn(Optional.of(profile));
      when(assignmentRepository.findAllByActivityIdAndEffectiveToIsNull(10L))
          .thenReturn(
              List.of(
                  assignment(1L, profile, LocalDate.MIN),
                  assignment(2L, profile, LocalDate.of(2020, 1, 1))));

      assertThatThrownBy(() -> catalog.synchronizeCurrent(10L, ReportingProfileKey.W2))
          .isInstanceOf(ReportPolicyParityException.class)
          .hasMessageContaining("multiple open");
    }

    @Test
    void createsAnOpenAssignmentForANewActivity() {
      ReportingProfile profile = profile(ReportingProfileKey.W2);
      when(reportingProfileRepository.findByKeyAndActiveTrue(ReportingProfileKey.W2))
          .thenReturn(Optional.of(profile));
      when(assignmentRepository.findAllByActivityIdAndEffectiveToIsNull(10L)).thenReturn(List.of());
      ArgumentCaptor<ActivityReportingProfileAssignment> captor =
          ArgumentCaptor.forClass(ActivityReportingProfileAssignment.class);

      catalog.synchronizeCurrent(10L, ReportingProfileKey.W2);

      verify(assignmentRepository).save(captor.capture());
      assertThat(captor.getValue().getActivityId()).isEqualTo(10L);
      assertThat(captor.getValue().getReportingProfile()).isSameAs(profile);
      assertThat(captor.getValue().getEffectiveFrom()).isEqualTo(TODAY);
      assertThat(captor.getValue().getEffectiveTo()).isNull();
    }

    @Test
    void failsClosedForAFutureOpenAssignment() {
      ReportingProfile profile = profile(ReportingProfileKey.W2);
      when(reportingProfileRepository.findByKeyAndActiveTrue(ReportingProfileKey.W2))
          .thenReturn(Optional.of(profile));
      when(assignmentRepository.findAllByActivityIdAndEffectiveToIsNull(10L))
          .thenReturn(List.of(assignment(1L, profile, TODAY.plusDays(1))));

      assertThatThrownBy(() -> catalog.synchronizeCurrent(10L, ReportingProfileKey.W2))
          .isInstanceOf(ReportPolicyParityException.class)
          .hasMessageContaining("future");
    }

    @Test
    void leavesAnExistingAssignmentWithTheSameProfileUntouched() {
      ReportingProfile profile = profile(ReportingProfileKey.W2);
      ActivityReportingProfileAssignment current =
          assignment(1L, profile, LocalDate.of(2020, 1, 1));
      when(reportingProfileRepository.findByKeyAndActiveTrue(ReportingProfileKey.W2))
          .thenReturn(Optional.of(profile));
      when(assignmentRepository.findAllByActivityIdAndEffectiveToIsNull(10L))
          .thenReturn(List.of(current));

      catalog.synchronizeCurrent(10L, ReportingProfileKey.W2);

      verify(assignmentRepository, never()).save(current);
      verify(assignmentRepository, never()).saveAndFlush(current);
    }

    @Test
    void replacesASameDayAssignmentWithoutCreatingAnOverlap() {
      ReportingProfile oldProfile = profile(ReportingProfileKey.W2);
      ReportingProfile newProfile = profile(ReportingProfileKey.SCHEDULE_C);
      ActivityReportingProfileAssignment current = assignment(1L, oldProfile, TODAY);
      when(reportingProfileRepository.findByKeyAndActiveTrue(ReportingProfileKey.SCHEDULE_C))
          .thenReturn(Optional.of(newProfile));
      when(assignmentRepository.findAllByActivityIdAndEffectiveToIsNull(10L))
          .thenReturn(List.of(current));

      catalog.synchronizeCurrent(10L, ReportingProfileKey.SCHEDULE_C);

      assertThat(current.getReportingProfile()).isSameAs(newProfile);
      verify(assignmentRepository).save(current);
    }

    @Test
    void closesTheOldRangeBeforeOpeningTheNewProfile() {
      ReportingProfile oldProfile = profile(ReportingProfileKey.W2);
      ReportingProfile newProfile = profile(ReportingProfileKey.SCHEDULE_C);
      ActivityReportingProfileAssignment current =
          assignment(1L, oldProfile, LocalDate.of(2020, 1, 1));
      when(reportingProfileRepository.findByKeyAndActiveTrue(ReportingProfileKey.SCHEDULE_C))
          .thenReturn(Optional.of(newProfile));
      when(assignmentRepository.findAllByActivityIdAndEffectiveToIsNull(10L))
          .thenReturn(List.of(current));

      catalog.synchronizeCurrent(10L, ReportingProfileKey.SCHEDULE_C);

      ArgumentCaptor<ActivityReportingProfileAssignment> newAssignment =
          ArgumentCaptor.forClass(ActivityReportingProfileAssignment.class);
      InOrder writes = inOrder(assignmentRepository);
      writes.verify(assignmentRepository).saveAndFlush(current);
      writes.verify(assignmentRepository).save(newAssignment.capture());
      assertThat(current.getEffectiveTo()).isEqualTo(TODAY.minusDays(1));
      assertThat(newAssignment.getValue().getEffectiveFrom()).isEqualTo(TODAY);
      assertThat(newAssignment.getValue().getEffectiveTo()).isNull();
      assertThat(newAssignment.getValue().getReportingProfile()).isSameAs(newProfile);
    }
  }

  @Nested
  class RemoveAll {

    @Test
    void deletesEveryAssignmentOwnedByTheActivity() {
      catalog.removeAll(10L);

      verify(assignmentRepository).deleteAllByActivityId(10L);
    }
  }

  private ReportingProfile profile(ReportingProfileKey key) {
    return ReportingProfile.builder().id((long) key.ordinal() + 1).key(key).active(true).build();
  }

  private ActivityReportingProfileAssignment assignment(
      Long id, ReportingProfile profile, LocalDate effectiveFrom) {
    return ActivityReportingProfileAssignment.builder()
        .id(id)
        .activityId(10L)
        .reportingProfile(profile)
        .effectiveFrom(effectiveFrom)
        .build();
  }
}
