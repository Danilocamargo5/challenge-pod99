package com.pod99;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
@ComponentScan(basePackages = {"com.pod99"})
public class AuthorizationServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AuthorizationServiceApplication.class, args);
        log.info("✅ POD99 Authorization Service started successfully on port 8080");
    }
}
