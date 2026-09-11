package com.pod99.common.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * Filter que wrappa o HttpServletRequest com ContentCachingRequestWrapper
 * Permite que o interceptor leia o body SEM consumir o InputStream
 * Assim o Spring consegue desserializar o @RequestBody normalmente
 */
@Slf4j
@Component
public class ContentCachingFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        log.info("🔍 [ContentCachingFilter] Wrappando request: {} {}", 
            request.getMethod(), request.getRequestURI());
        
        // Wrappa com ContentCachingRequestWrapper pra permitir múltiplas leituras
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        
        filterChain.doFilter(wrappedRequest, response);
    }
}
