package com.sajuppugi.fortune.talisman.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.talisman.domain.Talisman;
import com.sajuppugi.fortune.talisman.domain.Talisman.Status;
import com.sajuppugi.fortune.talisman.port.TalismanAssetAccess;
import com.sajuppugi.fortune.talisman.port.TalismanRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TalismanQueryService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final Duration DOWNLOAD_LIFETIME = Duration.ofMinutes(10);
    private final TalismanRepository talismans;
    private final ObjectProvider<TalismanAssetAccess> assetAccess;

    public TalismanQueryService(TalismanRepository talismans, ObjectProvider<TalismanAssetAccess> assetAccess) {
        this.talismans = talismans;
        this.assetAccess = assetAccess;
    }

    @Transactional(readOnly = true)
    public Talisman get(UUID userId, UUID talismanId) {
        return talismans.findOwned(userId, talismanId)
                .orElseThrow(() -> new ApiException(ErrorCode.TALISMAN_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Page list(UUID userId, String cursor, Integer size) {
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageSize < 1 || pageSize > MAX_SIZE) throw new ApiException(ErrorCode.INVALID_REQUEST);
        Cursor decoded = decode(cursor);
        List<Talisman> fetched = talismans.findOwnedPage(userId,
                decoded == null ? null : decoded.acquiredAt(), decoded == null ? null : decoded.id(), pageSize + 1);
        boolean hasNext = fetched.size() > pageSize;
        List<Talisman> items = hasNext ? List.copyOf(fetched.subList(0, pageSize)) : List.copyOf(fetched);
        String nextCursor = hasNext ? encode(items.get(items.size() - 1)) : null;
        return new Page(items, nextCursor, hasNext);
    }

    public Download download(UUID userId, UUID talismanId) {
        Talisman talisman = get(userId, talismanId);
        if (talisman.status() != Status.READY) throw new ApiException(ErrorCode.TALISMAN_NOT_READY);
        TalismanAssetAccess signer = assetAccess.getIfAvailable();
        if (signer == null) throw new ApiException(ErrorCode.TALISMAN_DOWNLOAD_UNAVAILABLE);
        return new Download(signer.signedDownloadUrl(talisman.assets().original(), DOWNLOAD_LIFETIME).toString(),
                DOWNLOAD_LIFETIME.toSeconds());
    }

    private String encode(Talisman value) {
        String raw = value.createdAt() + "|" + value.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decode(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException failure) {
            throw new ApiException(ErrorCode.INVALID_CURSOR);
        }
    }

    public record Page(List<Talisman> items, String nextCursor, boolean hasNext) {}
    public record Download(String url, long expiresInSeconds) {}
    private record Cursor(Instant acquiredAt, UUID id) {}
}
