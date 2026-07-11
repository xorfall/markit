package com.markit.platform.security;

import com.markit.identity.application.port.AccessTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests from a Bearer access token. A valid token puts an {@link AuthenticatedUser}
 * principal into the security context; everything downstream scopes to it (R-SEC-01).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String PREFIX = "Bearer ";

  private final AccessTokenService accessTokens;

  public JwtAuthenticationFilter(AccessTokenService accessTokens) {
    this.accessTokens = accessTokens;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String token = resolveToken(request);
    if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
      accessTokens.verify(token).ifPresent(userId -> authenticate(userId, request));
    }
    chain.doFilter(request, response);
  }

  /**
   * The token from the {@code Authorization: Bearer} header, or — as a fallback for the SSE stream,
   * since {@code EventSource} cannot set headers — from the {@code access_token} query parameter.
   */
  private static String resolveToken(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(PREFIX)) {
      return header.substring(PREFIX.length());
    }
    String param = request.getParameter("access_token");
    return (param != null && !param.isBlank()) ? param : null;
  }

  private void authenticate(
      com.markit.identity.domain.UserId userId, HttpServletRequest request) {
    var principal = new AuthenticatedUser(userId);
    var authentication =
        new UsernamePasswordAuthenticationToken(principal, null, List.of());
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
