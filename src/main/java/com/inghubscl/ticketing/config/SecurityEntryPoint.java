package com.inghubscl.ticketing.config;

import com.inghubscl.ticketing.common.ProblemDetailWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class SecurityEntryPoint implements AuthenticationEntryPoint {

  private final ProblemDetailWriter writer;

  public SecurityEntryPoint(ProblemDetailWriter writer) {
    this.writer = writer;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException authEx)
      throws IOException {
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"api\"");
    writer.write(response, HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication required");
  }
}
