package com.sajuppugi.operations;

import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class OperationsAlertService {
    private final JdbcOperationsAlerts alerts;
    private final ObjectProvider<OperationsAlertSink> sinks;
    private final Clock clock;
    public OperationsAlertService(JdbcOperationsAlerts alerts, ObjectProvider<OperationsAlertSink> sinks, Clock clock) {
        this.alerts = alerts; this.sinks = sinks; this.clock = clock;
    }
    public void deliver(UUID id) {
        var sink = sinks.getIfAvailable();
        if (sink == null) return;
        var lease = alerts.claim(id, clock.instant());
        if (lease.isEmpty()) return;
        boolean accepted;
        try { accepted = sink.send(lease.get().alert()); }
        catch (RuntimeException failure) { accepted = false; }
        alerts.finish(lease.get(), accepted, clock.instant());
    }
}
