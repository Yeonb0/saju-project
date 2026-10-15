package com.sajuppugi.gift.application;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Internal administrative capability. Reservation/authorization must join this transaction. */
public interface GiftMaintenanceRecoveryPort {
    void reopen(UUID order, Kind kind, BooleanSupplier authorizeAndReserve);
    enum Kind { PHONE_CLEANUP, PRIVATE_CLEANUP, PAYLOAD_REENCRYPTION }
}
