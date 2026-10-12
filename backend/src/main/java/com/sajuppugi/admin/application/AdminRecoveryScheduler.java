package com.sajuppugi.admin.application;

import com.sajuppugi.admin.infrastructure.JdbcAdminRecovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = {"app.admin.enabled", "app.admin.recovery-enabled"}, havingValue = "true")
public class AdminRecoveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(AdminRecoveryScheduler.class);
    private final JdbcAdminRecovery commands;
    private final AdminPaymentRecoveryService service;
    public AdminRecoveryScheduler(JdbcAdminRecovery commands, AdminPaymentRecoveryService service) { this.commands = commands; this.service = service; }
    @Scheduled(initialDelayString = "${app.admin.recovery-poll-delay:60000}", fixedDelayString = "${app.admin.recovery-poll-delay:60000}")
    public void run() {
        for (var id : commands.due()) {
            try { service.recover(id); }
            catch (RuntimeException failure) { log.warn("Admin recovery deferred: commandId={}", id); }
        }
    }
}
