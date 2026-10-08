package com.sajuppugi.fortune.reading.application;

import com.sajuppugi.fortune.reading.domain.OwnedReading;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import com.sajuppugi.fortune.talisman.port.TalismanFulfillmentPort;
import com.sajuppugi.fortune.talisman.port.TalismanFulfillmentPort.CreateTalisman;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BundledReadingFulfillmentService {
    private final ReadingRepository readings;
    private final TalismanFulfillmentPort talismans;

    public BundledReadingFulfillmentService(ReadingRepository readings, TalismanFulfillmentPort talismans) {
        this.readings = readings;
        this.talismans = talismans;
    }

    @Transactional
    public OwnedReading fulfill(ReadingPurchase purchase, OwnedReading reading, CreateTalisman command) {
        if (!reading.id().equals(command.readingId())
                || !reading.ownerUserId().equals(command.ownerUserId())
                || reading.fortuneType() != command.fortuneType()) {
            throw new IllegalArgumentException("Reading and talisman context must match");
        }
        var talisman = talismans.create(command);
        OwnedReading linked = reading.withTalisman(talisman.talismanId(), talisman.status().name());
        readings.fulfill(purchase, linked);
        return linked;
    }
}
