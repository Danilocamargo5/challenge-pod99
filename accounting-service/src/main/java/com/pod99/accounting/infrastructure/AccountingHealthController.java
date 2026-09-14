package com.pod99.accounting.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/health")
public class AccountingHealthController {
    
    @GetMapping
    public ResponseEntity<?> health() {
        log.info("🔵 [ACCOUNTING] HEALTH CHECK");
        return ResponseEntity.ok(Map.of(
            "service", "accounting-service",
            "status", "UP",
            "listener", "SQS FIFO pod99-accounting-queue.fifo"
        ));
    }
}
