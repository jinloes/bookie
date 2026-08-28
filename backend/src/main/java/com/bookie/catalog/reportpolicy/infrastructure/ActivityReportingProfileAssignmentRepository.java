package com.bookie.catalog.reportpolicy.infrastructure;

import com.bookie.catalog.reportpolicy.domain.ActivityReportingProfileAssignment;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
interface ActivityReportingProfileAssignmentRepository
    extends JpaRepository<ActivityReportingProfileAssignment, Long> {

  @Query(
      """
      SELECT assignment
      FROM ActivityReportingProfileAssignment assignment
      WHERE assignment.activityId = :activityId
        AND assignment.effectiveFrom <= :effectiveOn
        AND (
          assignment.effectiveTo IS NULL
          OR assignment.effectiveTo >= :effectiveOn
        )
      ORDER BY assignment.effectiveFrom DESC
      """)
  List<ActivityReportingProfileAssignment> findEffective(
      @Param("activityId") Long activityId, @Param("effectiveOn") LocalDate effectiveOn);

  @Query(
      """
      SELECT assignment
      FROM ActivityReportingProfileAssignment assignment
      WHERE assignment.activityId = :activityId
        AND assignment.effectiveFrom <= :effectiveTo
        AND (
          assignment.effectiveTo IS NULL
          OR assignment.effectiveTo >= :effectiveFrom
        )
      ORDER BY assignment.effectiveFrom ASC
      """)
  List<ActivityReportingProfileAssignment> findOverlapping(
      @Param("activityId") Long activityId,
      @Param("effectiveFrom") LocalDate effectiveFrom,
      @Param("effectiveTo") LocalDate effectiveTo);

  List<ActivityReportingProfileAssignment> findAllByActivityIdAndEffectiveToIsNull(Long activityId);

  @Modifying
  @Query(
      """
      DELETE FROM ActivityReportingProfileAssignment assignment
      WHERE assignment.activityId = :activityId
      """)
  void deleteAllByActivityId(@Param("activityId") Long activityId);
}
