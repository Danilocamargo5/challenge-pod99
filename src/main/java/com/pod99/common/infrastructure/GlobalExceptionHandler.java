package com.pod99.common.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    
    @ExceptionHandler(RequestContext.UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(
            RequestContext.UnauthorizedException ex) {
        
        log.warn("🔐 Requisição não autenticada: {}", ex.getMessage());
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "https://api.pod99.com/errors/unauthorized");
        response.put("title", "Unauthorized");
        response.put("status", 401);
        response.put("detail", ex.getMessage());
        response.put("timestamp", Instant.now().toString());
        
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("❌ Erro não tratado", ex);
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "https://api.pod99.com/errors/internal-error");
        response.put("title", "Internal Server Error");
        response.put("status", 500);
        response.put("detail", ex.getMessage());
        response.put("timestamp", Instant.now().toString());
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response);
    }
}
