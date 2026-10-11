package com.sajuppugi.fortune.reading.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReadingPurchaseStatusService {
    private final ReadingRepository repository;

    public ReadingPurchaseStatusService(ReadingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PurchaseStatus getByQuote(UUID buyerUserId, UUID quoteId) {
        if (buyerUserId == null || quoteId == null) throw new ApiException(ErrorCode.INVALID_REQUEST);
        ReadingPurchase purchase = repository.findPurchaseByQuote(buyerUserId, quoteId)
                .orElseThrow(() -> new ApiException(ErrorCode.PURCHASE_NOT_FOUND));
        var projection = ReadingPurchaseStatusPolicy.project(purchase.status(), purchase.compensationStatus());
        return new PurchaseStatus(purchase.id(), purchase.quoteId(), purchase.readingId(), purchase.status(),
                projection.compensationStatus(), projection.nextAction(), purchase.failureCode(),
                purchase.createdAt(), purchase.fulfilledAt());
    }

    public record PurchaseStatus(UUID purchaseId, UUID quoteId, UUID readingId, ReadingPurchase.Status status,
                                 ReadingPurchase.CompensationStatus compensationStatus,
                                 ReadingPurchaseStatusPolicy.NextAction nextAction, String failureCode,
                                 java.time.Instant createdAt, java.time.Instant fulfilledAt) {}
}
