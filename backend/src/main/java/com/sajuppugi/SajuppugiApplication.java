package com.sajuppugi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SajuppugiApplication {
    public static void main(String[] args) {
        SpringApplication.run(SajuppugiApplication.class, args);
    }
}
