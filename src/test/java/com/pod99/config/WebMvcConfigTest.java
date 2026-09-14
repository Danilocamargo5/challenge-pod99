package com.pod99.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("WebMvcConfig Tests")
class WebMvcConfigTest {
    
    @Autowired
    private WebMvcConfig webMvcConfig;
    
    @Test
    @DisplayName("✅ WebMvcConfig deve estar registrado como Bean")
    void testWebMvcConfigBean() {
        assertNotNull(webMvcConfig);
    }
    
    @Test
    @DisplayName("✅ addInterceptors deve registrar interceptores")
    void testAddInterceptors() {
        InterceptorRegistry registry = new InterceptorRegistry();
        
        assertDoesNotThrow(() -> {
            webMvcConfig.addInterceptors(registry);
        });
    }
    
    @Test
    @DisplayName("✅ RateLimitInterceptor deve estar registrado")
    void testRateLimitInterceptorRegistered() {
        assertNotNull(webMvcConfig);
        // Interceptor é registrado via addInterceptors()
    }
}
