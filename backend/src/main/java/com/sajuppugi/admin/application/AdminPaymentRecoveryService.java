package com.sajuppugi.admin.application;

import com.sajuppugi.admin.infrastructure.JdbcAdminRecovery;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.payment.port.PaymentProviderPort;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class AdminPaymentRecoveryService {
    private final JdbcAdminRecovery commands;
    private final AdminAccessService access;
    private final ObjectProvider<PaymentProviderPort> providers;
    private final boolean enabled;
    public AdminPaymentRecoveryService(JdbcAdminRecovery commands, AdminAccessService access, ObjectProvider<PaymentProviderPort> providers,
            @Value("${app.admin.recovery-enabled:false}") boolean enabled) { this.commands = commands; this.access = access; this.providers = providers; this.enabled = enabled; }
    public JdbcAdminRecovery.View request(UUID actor, UUID order, String key, JdbcAdminRecovery.Reason reason) {
        access.require(actor, true);
        if (!enabled || providers.getIfAvailable() == null) throw new ApiException(ErrorCode.PAYMENT_FULFILLMENT_UNAVAILABLE);
        UUID command = commands.begin(actor, order, key, reason);
        recover(command);
        return commands.view(actor, command);
    }
    public void recover(UUID command) {
        var provider = providers.getIfAvailable();
        if (!enabled || provider == null) return;
        var lease = commands.claim(command);
        if (lease.isEmpty()) return;
        PaymentProviderPort.Result evidence;
        try { evidence = provider.lookup(lease.get().order()); }
        catch (RuntimeException failure) { evidence = null; }
        commands.finish(lease.get(), evidence);
    }
}
