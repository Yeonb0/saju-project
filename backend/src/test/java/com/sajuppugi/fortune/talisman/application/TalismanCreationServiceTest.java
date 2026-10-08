package com.sajuppugi.fortune.talisman.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sajuppugi.fortune.calculation.domain.CalculationFacts;
import com.sajuppugi.fortune.calculation.domain.CalculationFacts.Element;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.talisman.domain.Talisman;
import com.sajuppugi.fortune.talisman.domain.Talisman.Animal;
import com.sajuppugi.fortune.talisman.domain.Talisman.AssetKeys;
import com.sajuppugi.fortune.talisman.domain.Talisman.Status;
import com.sajuppugi.fortune.talisman.domain.TalismanCompositionPolicy;
import com.sajuppugi.fortune.talisman.port.TalismanFulfillmentPort.CreateTalisman;
import com.sajuppugi.fortune.talisman.port.TalismanRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TalismanCreationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-08T03:00:00Z");
    private final TalismanRepository repository = org.mockito.Mockito.mock(TalismanRepository.class);
    private final TalismanCreationService service = new TalismanCreationService(repository,
            new TalismanCompositionPolicy(new Random(9)), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void composesAndStoresPendingTalisman() {
        CreateTalisman command = command();
        when(repository.findByReadingId(command.readingId())).thenReturn(Optional.empty());
        when(repository.createPending(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var fulfillment = service.create(command);

        assertThat(fulfillment.status()).isEqualTo(Status.PENDING);
        verify(repository).createPending(org.mockito.ArgumentMatchers.argThat(talisman ->
                talisman.id().equals(fulfillment.talismanId())
                        && talisman.ownerUserId().equals(command.ownerUserId())
                        && talisman.sourceReadingId().equals(command.readingId())
                        && talisman.fortuneType() == FortuneType.SUNEUNG
                        && talisman.element() == Element.WATER
                        && talisman.createdAt().equals(NOW)));
    }

    @Test
    void reusesStoredReadyTalismanWithoutRecomposing() {
        CreateTalisman command = command();
        Talisman ready = new Talisman(UUID.randomUUID(), command.ownerUserId(), command.readingId(),
                command.fortuneType(), Element.WATER, Animal.RABBIT, "문구", "설명", command.contentVersion(),
                Status.READY, new AssetKeys("original", "thumbnail", "share"), null, NOW, NOW.plusSeconds(5));
        when(repository.findByReadingId(command.readingId())).thenReturn(Optional.of(ready));

        assertThat(service.create(command).talismanId()).isEqualTo(ready.id());
        verify(repository, never()).createPending(any());
    }

    @Test
    void rejectsAStoredTalismanFromAnotherContext() {
        CreateTalisman command = command();
        Talisman mismatched = new Talisman(UUID.randomUUID(), UUID.randomUUID(), command.readingId(),
                command.fortuneType(), Element.WATER, Animal.RABBIT, "문구", "설명", command.contentVersion(),
                Status.PENDING, null, null, NOW, null);
        when(repository.findByReadingId(command.readingId())).thenReturn(Optional.of(mismatched));

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");
    }

    private CreateTalisman command() {
        return new CreateTalisman(UUID.randomUUID(), UUID.randomUUID(), FortuneType.SUNEUNG,
                facts(), "suneung-2026-v1");
    }

    private CalculationFacts facts() {
        Map<Element, Integer> counts = new EnumMap<>(Element.class);
        for (Element element : Element.values()) counts.put(element, 2);
        counts.put(Element.WATER, 0);
        return new CalculationFacts(null, null, null, null,
                new CalculationFacts.FiveElements(counts, List.of(), List.of(Element.WATER)),
                null, null, null, null);
    }
}
