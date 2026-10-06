package com.factech.nexus.shared.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra {@link RecentMfaInterceptor} sobre la API (`RF-SP-073`). */
@Configuration
public class RecentMfaWebConfig implements WebMvcConfigurer {

  private final RecentMfaInterceptor interceptor;

  public RecentMfaWebConfig(RecentMfaInterceptor interceptor) {
    this.interceptor = interceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registro) {
    registro.addInterceptor(interceptor).addPathPatterns("/api/**");
  }
}
