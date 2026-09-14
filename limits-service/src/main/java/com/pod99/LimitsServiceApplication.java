package com.pod99;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
@ComponentScan(basePackages = {"com.pod99"})
public class LimitsServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(LimitsServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(LimitsServiceApplication.class, args);
        log.info("✅ POD99 Limits Service started successfully on port 8082");
    }
}
