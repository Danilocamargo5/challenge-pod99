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
        
        // Wrappa com ContentCachingRequestWrapper pra permitir múltiplas leituras
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        
        // ⚠️ IMPORTANTE: Forçar leitura do body AQUI para que o cache seja populado
        // Sem isso, quando o Interceptor tenta ler, o buffer ainda está vazio
        if ("POST".equalsIgnoreCase(wrappedRequest.getMethod()) || 
            "PUT".equalsIgnoreCase(wrappedRequest.getMethod())) {
            wrappedRequest.getContentAsByteArray();  // Força leitura e cache
            log.debug("📦 Body cached in filter for method: {}", wrappedRequest.getMethod());
        }
        
        filterChain.doFilter(wrappedRequest, response);
    }
}
