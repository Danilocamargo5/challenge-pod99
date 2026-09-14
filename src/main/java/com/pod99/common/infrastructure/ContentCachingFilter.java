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
 * Permite que MÚLTIPLOS componentes leiam o body sem consumi-lo
 * 
 * ⚠️ NÃO leia o stream aqui! ContentCachingRequestWrapper cacheia automaticamente
 * quando alguém lê via getInputStream() ou getReader()
 */
@Slf4j
@Component
public class ContentCachingFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        // Envolve com ContentCachingRequestWrapper
        // Isso permite que MÚLTIPLOS leitores acessem o body
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        
        log.debug("📦 ContentCachingRequestWrapper installed for method: {}", wrappedRequest.getMethod());
        
        // Passa o request envolto pra resto da chain
        // O Spring e o Interceptor lerão do cache automaticamente
        filterChain.doFilter(wrappedRequest, response);
    }
}
