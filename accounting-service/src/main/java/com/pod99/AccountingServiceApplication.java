package com.pod99;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
@ComponentScan(basePackages = {"com.pod99"})
public class AccountingServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(AccountingServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AccountingServiceApplication.class, args);
        log.info("✅ POD99 Accounting Service started successfully (SQS Listener active)");
    }
}
