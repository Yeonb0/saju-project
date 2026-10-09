package com.sajuppugi.fortune.reading.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.reading.domain.OwnedReading;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReadingQueryService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private final ReadingRepository readings;

    public ReadingQueryService(ReadingRepository readings) {
        this.readings = readings;
    }

    public OwnedReading get(UUID userId, UUID readingId) {
        return readings.findOwnedReading(userId, readingId)
                .orElseThrow(() -> new ApiException(ErrorCode.READING_NOT_FOUND));
    }

    public Page list(UUID userId, FortuneType fortuneType, UUID personId, String cursor, Integer size) {
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageSize < 1 || pageSize > MAX_SIZE) throw new ApiException(ErrorCode.INVALID_REQUEST);
        Cursor decoded = decode(cursor);
        List<OwnedReading> fetched = readings.findOwnedReadings(userId, fortuneType, personId,
                decoded == null ? null : decoded.createdAt(), decoded == null ? null : decoded.id(), pageSize + 1);
        boolean hasNext = fetched.size() > pageSize;
        List<OwnedReading> items = hasNext ? List.copyOf(fetched.subList(0, pageSize)) : List.copyOf(fetched);
        String nextCursor = hasNext ? encode(items.get(items.size() - 1)) : null;
        return new Page(items, nextCursor, hasNext);
    }

    private String encode(OwnedReading reading) {
        String value = reading.createdAt() + "|" + reading.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException("invalid cursor parts");
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_CURSOR);
        }
    }

    public record Page(List<OwnedReading> items, String nextCursor, boolean hasNext) {}
    private record Cursor(Instant createdAt, UUID id) {}
}
