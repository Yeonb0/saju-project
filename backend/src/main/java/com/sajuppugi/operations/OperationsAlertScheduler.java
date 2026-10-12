package com.sajuppugi.operations;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.operations.alerts.enabled", havingValue = "true")
public class OperationsAlertScheduler {
    private static final Logger log = LoggerFactory.getLogger(OperationsAlertScheduler.class);
    private final JdbcOperationsAlerts alerts;
    private final OperationsAlertService service;
    private final Clock clock;
    private UUID cursor;
    public OperationsAlertScheduler(JdbcOperationsAlerts alerts, OperationsAlertService service, Clock clock) { this.alerts = alerts; this.service = service; this.clock = clock; }
    @Scheduled(fixedDelayString = "${app.operations.alerts.poll-delay:60000}")
    public void run() {
        for (var problem : alerts.candidates(clock.instant())) {
            try { alerts.observe(problem, clock.instant()); }
            catch (RuntimeException failure) { log.warn("Operations incident capture deferred: kind={}", problem.kind()); }
        }
        var open = alerts.openAfter(cursor);
        for (var id : open) {
            try { alerts.resolveIfHealthy(id, clock.instant()); }
            catch (RuntimeException failure) { log.warn("Operations incident resolution deferred: incidentId={}", id); }
            cursor = id;
        }
        if (open.size() < 100) cursor = null;
        for (var id : alerts.due(clock.instant())) {
            try { service.deliver(id); }
            catch (RuntimeException failure) { log.warn("Operations alert delivery deferred: alertId={}", id); }
        }
    }
}
