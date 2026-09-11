package com.pod99;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class Pod99Application {

    private static final Logger logger = LoggerFactory.getLogger(Pod99Application.class);

    public static void main(String[] args) {
        SpringApplication.run(Pod99Application.class, args);
        logger.info("✅ POD99 Authorization Platform started successfully");
    }
}
