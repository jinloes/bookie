package com.bookie.intake.api;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bookie.intake.sources.AutoImportPollingService;
import com.bookie.util.BackgroundWorkControl;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IntakeControlController.class)
class IntakeControlControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AutoImportPollingService autoImport;
  @MockitoBean private BackgroundWorkControl workControl;

  @Nested
  class RunAutoImport {

    @Test
    void returnsTheNumberOfQueuedItems() throws Exception {
      when(autoImport.runNow()).thenReturn(3);

      mockMvc
          .perform(post("/api/auto-import/run"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.queued", is(3)));

      verifyNoInteractions(workControl);
    }
  }

  @Nested
  class Drain {

    @Test
    void requestsADrainAndAcceptsWithoutABody() throws Exception {
      mockMvc
          .perform(post("/api/intake/drain"))
          .andExpect(status().isAccepted())
          .andExpect(content().string(""));

      verify(workControl).requestDrain();
      verifyNoInteractions(autoImport);
    }
  }
}
