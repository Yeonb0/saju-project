package com.sajuppugi.admin.application;

import com.sajuppugi.admin.infrastructure.JdbcAdminRefundCases;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.payment.application.CashRefundPolicy;
import com.sajuppugi.payment.application.CashRefundService;
import com.sajuppugi.payment.port.PaymentProviderPort;
import com.sajuppugi.wallet.application.RemainingTopUpRefund;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class AdminCashRefundService {
    private final JdbcAdminRefundCases cases;
    private final CashRefundService refunds;
    private final CashRefundPolicy policy;
    private final ObjectProvider<PaymentProviderPort> providers;
    public AdminCashRefundService(JdbcAdminRefundCases cases, CashRefundService refunds, CashRefundPolicy policy,
                                  ObjectProvider<PaymentProviderPort> providers) {
        this.cases = cases; this.refunds = refunds; this.policy = policy; this.providers = providers;
    }
    public JdbcAdminRefundCases.CaseView execute(UUID actor, UUID id, String key, RemainingTopUpRefund expected) {
        if (!policy.enabled() || providers.getIfAvailable() == null) throw new ApiException(ErrorCode.PAYMENT_FULFILLMENT_UNAVAILABLE);
        UUID order;
        try { order = cases.reserve(actor, id, key, expected); }
        catch (org.springframework.dao.DataIntegrityViolationException collision) { order = cases.reserve(actor, id, key, expected); }
        refunds.recover(order);
        return cases.view(actor, id);
    }
}
