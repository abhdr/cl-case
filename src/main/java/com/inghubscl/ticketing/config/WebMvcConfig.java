package com.inghubscl.ticketing.config;

import com.inghubscl.ticketing.idempotency.IdempotencyInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  private final IdempotencyInterceptor idempotencyInterceptor;

  public WebMvcConfig(IdempotencyInterceptor idempotencyInterceptor) {
    this.idempotencyInterceptor = idempotencyInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(idempotencyInterceptor).addPathPatterns("/api/events/*/reservations");
  }
}
