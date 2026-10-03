package com.sajuppugi.common.logging;

import java.util.UUID;
import org.slf4j.MDC;

public final class TraceContext {
    public static final String KEY = "traceId";

    private TraceContext() {
    }

    public static String currentId() {
        String id = MDC.get(KEY);
        return id == null ? UUID.randomUUID().toString() : id;
    }
}
