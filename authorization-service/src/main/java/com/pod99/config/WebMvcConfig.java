package com.pod99.config;

import com.pod99.common.infrastructure.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração de interceptors de requisição HTTP
 * Registra o RateLimitInterceptor para aplicar rate limiting global
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    
    private final RateLimitInterceptor rateLimitInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
            .addPathPatterns("/v1/**")  // Aplicar rate limiting a todas as APIs v1
            .order(1);  // Executar antes de outros interceptors
    }
}
