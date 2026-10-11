package com.sajuppugi.fortune.reading.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.reading.generation-context-protection")
public record GenerationContextProtectionProperties(String encryptionKey) {}
