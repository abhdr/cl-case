package com.inghubscl.ticketing.config;

import com.inghubscl.ticketing.common.ProblemDetailWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class SecurityAccessDeniedHandler implements AccessDeniedHandler {

  private final ProblemDetailWriter writer;

  public SecurityAccessDeniedHandler(ProblemDetailWriter writer) {
    this.writer = writer;
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    writer.write(
        response,
        HttpStatus.FORBIDDEN,
        "Forbidden",
        "Access denied: insufficient permissions for this resource");
  }
}
