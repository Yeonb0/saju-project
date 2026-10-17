package com.sajuppugi.fortune.generation.infrastructure;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class LinerGenerationExecutorConfiguration {
    @Bean(name = "linerGenerationExecutor")
    public Executor linerGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("liner-generation-");
        executor.initialize();
        return executor;
    }
}
