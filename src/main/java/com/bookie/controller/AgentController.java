package com.bookie.controller;

import com.bookie.service.AgentService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

  private final AgentService agentService;

  @PostMapping("/expense")
  public AgentService.AgentResponse submitExpense(@RequestBody Map<String, String> body) {
    String message = body.get("message");
    if (StringUtils.isBlank(message)) {
      throw new IllegalArgumentException("Message cannot be empty");
    }
    return agentService.processExpenseMessage(message);
  }
}
