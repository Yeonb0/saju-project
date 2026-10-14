package com.sajuppugi.gift.infrastructure;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.gift.application.GiftMaintenanceRecoveryPort;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
public class JdbcGiftMaintenanceRecovery implements GiftMaintenanceRecoveryPort {
    private final JdbcTemplate jdbc;
    private final JdbcGiftPurchaseWork work;
    private final GiftPayloadCipher cipher;
    private final boolean phone, privacy, reencrypt;
    public JdbcGiftMaintenanceRecovery(JdbcTemplate jdbc, JdbcGiftPurchaseWork work, GiftPayloadCipher cipher,
            @Value("${app.gift.phone-cleanup-enabled:false}") boolean phone,
            @Value("${app.gift.retention-enabled:false}") boolean privacy,
            @Value("${app.gift.payload-reencryption-enabled:false}") boolean reencrypt) {
        this.jdbc = jdbc; this.work = work; this.cipher = cipher;
        this.phone = phone; this.privacy = privacy; this.reencrypt = reencrypt;
    }
    @Override @Transactional(timeout = 10)
    public void reopen(UUID order, Kind kind, BooleanSupplier authorizeAndReserve) {
        if (order == null || kind == null || authorizeAndReserve == null) throw new ApiException(ErrorCode.INVALID_REQUEST);
        work.get(order, true);
        if (jdbc.query("SELECT id FROM gift_order_drafts WHERE id = ? FOR NO KEY UPDATE",
                (rs,row) -> rs.getObject(1, UUID.class), order).isEmpty()) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        // Reserve under the order lock; a durable replay must not reset another retry cycle.
        if (!authorizeAndReserve.getAsBoolean()) return;
        int changed = switch (kind) {
            case PHONE_CLEANUP -> phone ? jdbc.update("UPDATE gift_order_drafts SET phone_cleanup_failures = 0,phone_cleanup_next_attempt_at = NULL WHERE id = ? AND recipient_phone_masked IS NOT NULL AND phone_purged_at IS NULL AND phone_cleanup_failures = 5", order) : 0;
            case PRIVATE_CLEANUP -> privacy ? jdbc.update("UPDATE gift_order_drafts SET private_cleanup_failures = 0,private_cleanup_next_attempt_at = NULL WHERE id = ? AND private_payload_purged_at IS NULL AND private_cleanup_failures = 5", order) : 0;
            case PAYLOAD_REENCRYPTION -> reencrypt && cipher.versionedActive() ? jdbc.update("DELETE FROM gift_payload_reencryption_work WHERE order_id = ? AND active_key_id = ? AND state = 'REVIEW_REQUIRED' AND failures = 5", order, cipher.activeKeyId()) : 0;
        };
        if (changed != 1) throw new ApiException(ErrorCode.INVALID_REQUEST);
        // Existing workers recheck purpose, leases and key configuration before doing any work.
    }
}
