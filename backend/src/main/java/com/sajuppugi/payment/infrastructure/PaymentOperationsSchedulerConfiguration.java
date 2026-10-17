package com.sajuppugi.payment.infrastructure;

import com.sajuppugi.payment.application.PaymentOperationsSchedulerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class PaymentOperationsSchedulerConfiguration {
    @Bean(name = "paymentOperationsScheduler")
    TaskScheduler paymentOperationsScheduler(PaymentOperationsSchedulerProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.poolSize());
        scheduler.setThreadNamePrefix("payment-operations-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}
