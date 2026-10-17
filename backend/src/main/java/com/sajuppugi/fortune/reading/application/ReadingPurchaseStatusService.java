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
    private final GenerationPreviewFactory previews;

    public ReadingPurchaseStatusService(ReadingRepository repository, GenerationPreviewFactory previews) {
        this.repository = repository;
        this.previews = previews;
    }

    @Transactional(readOnly = true)
    public PurchaseStatus getByQuote(UUID buyerUserId, UUID quoteId) {
        if (buyerUserId == null || quoteId == null) throw new ApiException(ErrorCode.INVALID_REQUEST);
        ReadingPurchase purchase = repository.findPurchaseByQuote(buyerUserId, quoteId)
                .orElseThrow(() -> new ApiException(ErrorCode.PURCHASE_NOT_FOUND));
        var projection = ReadingPurchaseStatusPolicy.project(purchase.status(), purchase.compensationStatus());
        GenerationPreviewFactory.GenerationPreview preview = switch (purchase.status()) {
            case CREATED, DEBITED, GENERATING -> repository.findGenerationContext(buyerUserId, quoteId)
                    .map(context -> previews.create(context, purchase.createdAt())).orElse(null);
            default -> null;
        };
        return new PurchaseStatus(purchase.id(), purchase.quoteId(), purchase.readingId(), purchase.status(),
                projection.compensationStatus(), projection.nextAction(), purchase.failureCode(),
                purchase.createdAt(), purchase.fulfilledAt(), preview);
    }

    public record PurchaseStatus(UUID purchaseId, UUID quoteId, UUID readingId, ReadingPurchase.Status status,
                                 ReadingPurchase.CompensationStatus compensationStatus,
                                 ReadingPurchaseStatusPolicy.NextAction nextAction, String failureCode,
                                 java.time.Instant createdAt, java.time.Instant fulfilledAt,
                                 GenerationPreviewFactory.GenerationPreview generationPreview) {}
}
