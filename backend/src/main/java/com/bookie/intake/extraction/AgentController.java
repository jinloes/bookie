package com.bookie.intake.extraction;

import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

  private final AgentService agentService;

  @Operation(operationId = "processAgentMessage")
  @PostMapping("/transaction")
  public AgentService.AgentResponse submit(@RequestBody Map<String, String> body) {
    return process(body);
  }

  private AgentService.AgentResponse process(Map<String, String> body) {
    return agentService.processMessage(requiredMessage(body));
  }

  private String requiredMessage(Map<String, String> body) {
    String message = body.get("message");
    if (StringUtils.isBlank(message)) {
      throw new IllegalArgumentException("Message cannot be empty");
    }
    return message;
  }
}
