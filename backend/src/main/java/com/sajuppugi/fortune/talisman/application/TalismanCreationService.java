package com.sajuppugi.fortune.talisman.application;

import com.sajuppugi.fortune.talisman.domain.Talisman;
import com.sajuppugi.fortune.talisman.domain.Talisman.Status;
import com.sajuppugi.fortune.talisman.domain.TalismanCompositionPolicy;
import com.sajuppugi.fortune.talisman.port.TalismanFulfillmentPort;
import com.sajuppugi.fortune.talisman.port.TalismanFulfillmentPort.CreateFromStoredFacts;
import com.sajuppugi.fortune.talisman.port.TalismanRepository;
import com.sajuppugi.fortune.talisman.port.TalismanRenderJobRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TalismanCreationService implements TalismanFulfillmentPort {
    private final TalismanRepository talismans;
    private final TalismanRenderJobRepository renderJobs;
    private final TalismanCompositionPolicy compositionPolicy;
    private final Clock clock;

    @Autowired
    public TalismanCreationService(TalismanRepository talismans, TalismanRenderJobRepository renderJobs, Clock clock) {
        this(talismans, renderJobs, new TalismanCompositionPolicy(), clock);
    }

    TalismanCreationService(TalismanRepository talismans, TalismanRenderJobRepository renderJobs,
                            TalismanCompositionPolicy compositionPolicy, Clock clock) {
        this.talismans = talismans;
        this.renderJobs = renderJobs;
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
        // This is in the same short transaction as the reading fulfillment. The renderer itself runs later.
        if (stored.status() == Status.PENDING) renderJobs.enqueue(stored.id(), clock.instant());
        return new TalismanFulfillment(stored.id(), stored.status());
    }

    @Override
    @Transactional
    public TalismanFulfillment createFromStoredFacts(CreateFromStoredFacts command) {
        Talisman stored = talismans.findByReadingId(command.readingId()).orElseGet(() -> {
            var composition = compositionPolicy.compose(command.fortuneType(), command.fiveElements());
            return talismans.createPending(new Talisman(UUID.randomUUID(), command.ownerUserId(), command.readingId(),
                    command.fortuneType(), composition.element(), composition.animal(), composition.phrase(),
                    composition.description(), composition.visual(), command.contentVersion(), Status.PENDING, null, null,
                    clock.instant(), null));
        });
        if (!stored.ownerUserId().equals(command.ownerUserId()) || stored.fortuneType() != command.fortuneType()
                || !stored.contentVersion().equals(command.contentVersion()) || stored.status() == Status.FAILED) {
            throw new IllegalStateException("Stored talisman does not match its reading context");
        }
        if (stored.status() == Status.PENDING) renderJobs.enqueue(stored.id(), clock.instant());
        return new TalismanFulfillment(stored.id(), stored.status());
    }

    private Talisman createPending(CreateTalisman command) {
        var composition = compositionPolicy.compose(command.fortuneType(), command.facts());
        Talisman pending = new Talisman(UUID.randomUUID(), command.ownerUserId(), command.readingId(),
                command.fortuneType(), composition.element(), composition.animal(), composition.phrase(),
                composition.description(), composition.visual(), command.contentVersion(), Status.PENDING, null, null, clock.instant(), null);
        return talismans.createPending(pending);
    }
}
