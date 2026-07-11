package com.markit.platform.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.markit.identity.application.port.AccessTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PingController.class)
@AutoConfigureMockMvc(addFilters = false) // ping is public; skip the security filter chain in this slice
class PingControllerTest {

  @Autowired MockMvc mockMvc;

  // The security JwtAuthenticationFilter is a Filter bean picked up by @WebMvcTest; satisfy its dep.
  @MockitoBean AccessTokenService accessTokenService;

  @Test
  void should_ReturnOkStatus_When_PingCalled() throws Exception {
    mockMvc.perform(get("/api/v1/ping"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.service").value("markit-backend"));
  }
}
