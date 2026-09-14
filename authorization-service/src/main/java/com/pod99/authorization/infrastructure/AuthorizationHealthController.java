package com.pod99.authorization.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/health")
public class AuthorizationHealthController {
    
    @GetMapping
    public ResponseEntity<?> health() {
        log.info("🔵 [AUTHORIZATION] HEALTH CHECK");
        return ResponseEntity.ok(Map.of(
            "service", "authorization-service",
            "status", "UP",
            "port", 8080
        ));
    }
}
