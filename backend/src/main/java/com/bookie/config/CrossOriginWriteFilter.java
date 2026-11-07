package com.bookie.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects state-changing requests sent by a browser page whose origin is not an allowed client.
 *
 * <p>Multipart and body-less POSTs are CORS "simple requests": a page on any site can send them to
 * this unauthenticated loopback API without a preflight, and CORS only hides the response. Browsers
 * always attach {@code Origin} to such requests, so an origin outside {@code cors.allowed-origins}
 * is refused here. Requests without {@code Origin} come from non-browser clients and pass. {@code
 * Sec-Fetch-Site} is not used because packaged Tauri origins are cross-site to {@code
 * http://localhost:48763}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CrossOriginWriteFilter extends OncePerRequestFilter {

  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

  private final Set<String> allowedOrigins;
  private final ObjectMapper objectMapper;

  public CrossOriginWriteFilter(
      @Value("${cors.allowed-origins}") String allowedOrigins, ObjectMapper objectMapper) {
    this.allowedOrigins =
        Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toUnmodifiableSet());
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String origin = request.getHeader(HttpHeaders.ORIGIN);
    if (SAFE_METHODS.contains(request.getMethod())
        || origin == null
        || allowedOrigins.contains(origin)) {
      filterChain.doFilter(request, response);
      return;
    }
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getWriter(),
        new ApiErrorResponse("FORBIDDEN", "Cross-origin request rejected", Map.of()));
  }
}
