package com.bookie.intake.api;

import com.bookie.intake.application.BackgroundJobService;
import com.bookie.intake.application.InboxQueryService;
import com.bookie.intake.application.IntakeJobKickoff;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/inbox")
@RequiredArgsConstructor
public class InboxController {

  private final InboxQueryService queryService;
  private final BackgroundJobService backgroundJobService;
  private final IntakeJobKickoff jobKickoff;

  @Operation(operationId = "getInboxItems")
  @GetMapping
  public List<InboxItemResponse> findAll() {
    return queryService.findAll().stream().map(InboxItemResponse::from).toList();
  }

  @Operation(operationId = "getInboxItem")
  @GetMapping("/{id}")
  public InboxItemResponse findById(@PathVariable Long id) {
    return InboxItemResponse.from(queryService.findById(id));
  }

  @Operation(operationId = "getInboxItemJobs")
  @GetMapping("/{id}/jobs")
  public List<BackgroundJobResponse> findJobs(@PathVariable Long id) {
    return queryService.findJobs(id).stream().map(BackgroundJobResponse::from).toList();
  }

  @Operation(operationId = "retryInboxJob")
  @PostMapping("/jobs/{jobId}/retry")
  public BackgroundJobResponse retry(@PathVariable Long jobId) {
    BackgroundJobResponse response = BackgroundJobResponse.from(backgroundJobService.retry(jobId));
    jobKickoff.runJob(jobId);
    return response;
  }
}
