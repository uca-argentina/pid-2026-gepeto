package com.aparcar.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@EnableAsync
@EnableScheduling
@SpringBootApplication
@EnableMethodSecurity
public class AparcarApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AparcarApiApplication.class, args);
    }

}
