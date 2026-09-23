package com.bookie.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects requests whose Host hostname is not a configured loopback name.
 *
 * <p>Binding to 127.0.0.1 does not stop DNS rebinding: a web page whose hostname re-resolves to
 * 127.0.0.1 becomes same-origin with this unauthenticated API, so CORS never applies. Such requests
 * still carry the attacker's hostname in the Host header. Only the hostname is compared because the
 * Vite dev proxy forwards {@code localhost:5173} and Tauri sends {@code localhost:48763}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoopbackHostFilter extends OncePerRequestFilter {

  private static final String FORBIDDEN_BODY = "{\"message\":\"Forbidden host\"}";

  private final Set<String> allowedHosts;

  public LoopbackHostFilter(
      @Value("${bookie.security.allowed-hosts:localhost,127.0.0.1,::1}") String allowedHosts) {
    this.allowedHosts =
        Arrays.stream(allowedHosts.split(","))
            .map(LoopbackHostFilter::normalize)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (allowedHosts.contains(normalize(request.getServerName()))) {
      filterChain.doFilter(request, response);
      return;
    }
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(FORBIDDEN_BODY);
  }

  private static String normalize(String host) {
    return Strings.CS.removeEnd(
        Strings.CS.removeStart(StringUtils.trimToEmpty(host).toLowerCase(Locale.ROOT), "["), "]");
  }
}
