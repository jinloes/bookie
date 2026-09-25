package com.bookie.intake.api;

import com.bookie.intake.application.BackgroundJobService;
import com.bookie.intake.application.InboxCommands;
import com.bookie.intake.application.InboxEventStream;
import com.bookie.intake.application.InboxQueryService;
import com.bookie.intake.application.VenmoImportCommand;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v2/inbox")
@RequiredArgsConstructor
public class InboxController {

  private final InboxQueryService queryService;
  private final InboxCommands inboxCommands;
  private final InboxEventStream eventStream;
  private final InboxItemResponseAssembler responseAssembler;
  private final BackgroundJobService backgroundJobService;

  @Operation(operationId = "getInboxItems")
  @GetMapping
  public List<InboxItemResponse> findActive() {
    return responseAssembler.toResponses(queryService.findActive());
  }

  @Operation(operationId = "subscribeInboxEvents")
  @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribe() {
    return eventStream.subscribe();
  }

  @Operation(operationId = "getInboxItem")
  @GetMapping("/{id}")
  public InboxItemResponse findById(@PathVariable Long id) {
    return responseAssembler.toResponse(queryService.findById(id));
  }

  @Operation(operationId = "getInboxItemJobs")
  @GetMapping("/{id}/jobs")
  public List<BackgroundJobResponse> findJobs(@PathVariable Long id) {
    return queryService.findJobs(id).stream().map(BackgroundJobResponse::from).toList();
  }

  @Operation(operationId = "saveInboxItem")
  @PostMapping("/{id}/save")
  public InboxItemResponse save(
      @PathVariable Long id, @Valid @RequestBody SaveInboxItemRequest request) {
    return responseAssembler.toResponse(inboxCommands.save(id, request.toCommand()));
  }

  @Operation(operationId = "reparseInboxItem")
  @PostMapping("/{id}/reparse")
  public InboxItemResponse reparse(@PathVariable Long id) {
    return responseAssembler.toResponse(inboxCommands.reparse(id));
  }

  @Operation(operationId = "dismissInboxItem")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void dismiss(@PathVariable Long id) {
    inboxCommands.dismiss(id);
  }

  @Operation(operationId = "importVenmoInboxItems")
  @PostMapping(value = "/imports/venmo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public VenmoImportResponse importVenmo(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "counterpartyId", required = false) Long counterpartyId,
      @RequestParam(value = "activityId", required = false) Long activityId,
      @RequestParam(value = "propertyId", required = false) Long propertyId)
      throws IOException {
    return VenmoImportResponse.from(
        inboxCommands.importVenmo(
            VenmoImportCommand.builder()
                .content(file.getBytes())
                .fileName(file.getOriginalFilename())
                .counterpartyId(counterpartyId)
                .activityId(activityId)
                .propertyId(propertyId)
                .build()));
  }

  @Operation(operationId = "retryInboxJob")
  @PostMapping("/jobs/{jobId}/retry")
  public BackgroundJobResponse retry(@PathVariable Long jobId) {
    return BackgroundJobResponse.from(backgroundJobService.retry(jobId));
  }
}
