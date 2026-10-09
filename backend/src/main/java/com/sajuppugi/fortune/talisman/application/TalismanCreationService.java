package com.sajuppugi.fortune.talisman.application;

import com.sajuppugi.fortune.talisman.domain.Talisman;
import com.sajuppugi.fortune.talisman.domain.Talisman.Status;
import com.sajuppugi.fortune.talisman.domain.TalismanCompositionPolicy;
import com.sajuppugi.fortune.talisman.port.TalismanFulfillmentPort;
import com.sajuppugi.fortune.talisman.port.TalismanRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TalismanCreationService implements TalismanFulfillmentPort {
    private final TalismanRepository talismans;
    private final TalismanCompositionPolicy compositionPolicy;
    private final Clock clock;

    @Autowired
    public TalismanCreationService(TalismanRepository talismans, Clock clock) {
        this(talismans, new TalismanCompositionPolicy(), clock);
    }

    TalismanCreationService(TalismanRepository talismans, TalismanCompositionPolicy compositionPolicy, Clock clock) {
        this.talismans = talismans;
        this.compositionPolicy = compositionPolicy;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TalismanFulfillment create(CreateTalisman command) {
        Talisman stored = talismans.findByReadingId(command.readingId()).orElseGet(() -> createPending(command));
        if (!stored.ownerUserId().equals(command.ownerUserId())
                || stored.fortuneType() != command.fortuneType()
                || !stored.contentVersion().equals(command.contentVersion())) {
            throw new IllegalStateException("Stored talisman does not match its reading context");
        }
        if (stored.status() == Status.FAILED) {
            throw new IllegalStateException("Failed talisman cannot fulfill a reading");
        }
        return new TalismanFulfillment(stored.id(), stored.status());
    }

    private Talisman createPending(CreateTalisman command) {
        var composition = compositionPolicy.compose(command.fortuneType(), command.facts());
        Talisman pending = new Talisman(UUID.randomUUID(), command.ownerUserId(), command.readingId(),
                command.fortuneType(), composition.element(), composition.animal(), composition.phrase(),
                composition.description(), command.contentVersion(), Status.PENDING, null, null, clock.instant(), null);
        return talismans.createPending(pending);
    }
}
