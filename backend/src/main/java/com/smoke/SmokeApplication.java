package com.smoke;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmokeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmokeApplication.class, args);
    }
}
