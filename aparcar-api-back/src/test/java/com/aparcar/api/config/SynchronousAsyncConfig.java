package com.aparcar.api.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;

@EnableAsync
@TestConfiguration
public class SynchronousAsyncConfig {
    @Bean
    public Executor taskExecutor() {
        // Returns an executor that executes every task in the caller's thread
        return Runnable::run;
    }
}
