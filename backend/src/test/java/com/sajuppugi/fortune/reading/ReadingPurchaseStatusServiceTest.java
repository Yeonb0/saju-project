package com.sajuppugi.fortune.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sajuppugi.fortune.reading.application.GenerationPreviewFactory;
import com.sajuppugi.fortune.reading.application.ReadingPurchaseStatusService;
import com.sajuppugi.fortune.reading.domain.GenerationResumeContext;
import com.sajuppugi.fortune.reading.domain.ReadingPurchase;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadingPurchaseStatusServiceTest {
    @Test
    void exposesPreviewOnlyForOwnedProcessingPurchase() {
        ReadingRepository repository = mock(ReadingRepository.class);
        GenerationPreviewFactory factory = mock(GenerationPreviewFactory.class);
        GenerationResumeContext context = mock(GenerationResumeContext.class);
        UUID buyerId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-15T03:00:00Z");
        ReadingPurchase purchase = purchase(buyerId, quoteId, ReadingPurchase.Status.GENERATING, createdAt);
        var preview = new GenerationPreviewFactory.GenerationPreview("dog", "술", "개", "분석하고 있어요.");
        when(repository.findPurchaseByQuote(buyerId, quoteId)).thenReturn(Optional.of(purchase));
        when(repository.findGenerationContext(buyerId, quoteId)).thenReturn(Optional.of(context));
        when(factory.create(context, createdAt)).thenReturn(preview);

        var result = new ReadingPurchaseStatusService(repository, factory).getByQuote(buyerId, quoteId);

        assertThat(result.generationPreview()).isEqualTo(preview);
        verify(repository).findGenerationContext(buyerId, quoteId);
    }

    private ReadingPurchase purchase(UUID buyerId, UUID quoteId, ReadingPurchase.Status status, Instant createdAt) {
        return new ReadingPurchase(UUID.randomUUID(), buyerId, UUID.randomUUID(), quoteId, UUID.randomUUID(),
                null, null, "OVERALL_FLOW", UUID.randomUUID(), status, null,
                ReadingPurchase.CompensationStatus.NOT_REQUIRED, null, createdAt, null);
    }
}
