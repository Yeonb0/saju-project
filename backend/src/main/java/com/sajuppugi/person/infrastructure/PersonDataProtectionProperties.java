package com.sajuppugi.person.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.person")
public record PersonDataProtectionProperties(String encryptionKey) {}
