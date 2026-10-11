package com.bookie.intake.api;

import com.bookie.intake.sources.AutoImportPollingService;
import com.bookie.util.BackgroundWorkControl;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lets the desktop app start intake work that no longer runs on a timer in on-demand mode. */
@RestController
@RequiredArgsConstructor
public class IntakeControlController {

  private final AutoImportPollingService autoImport;
  private final BackgroundWorkControl workControl;

  public record AutoImportRunResponse(int queued) {}

  @Operation(operationId = "runAutoImport")
  @PostMapping("/api/auto-import/run")
  public AutoImportRunResponse runAutoImport() {
    return new AutoImportRunResponse(autoImport.runNow());
  }

  @Operation(operationId = "drainIntake")
  @PostMapping("/api/intake/drain")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void drain() {
    workControl.requestDrain();
  }
}
