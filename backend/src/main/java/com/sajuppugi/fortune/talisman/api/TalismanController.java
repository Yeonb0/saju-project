package com.sajuppugi.fortune.talisman.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.talisman.application.TalismanQueryService;
import com.sajuppugi.fortune.talisman.domain.Talisman;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/talismans")
public class TalismanController {
    private final TalismanQueryService service;

    public TalismanController(TalismanQueryService service) { this.service = service; }

    @GetMapping
    @Operation(operationId = "listTalismans")
    public ApiResponse<TalismanPage> list(Authentication authentication,
            @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer size) {
        TalismanQueryService.Page page = service.list(userId(authentication), cursor, size);
        return ApiResponse.of(new TalismanPage(page.items().stream().map(TalismanView::from).toList(),
                page.nextCursor(), page.hasNext()));
    }

    @GetMapping("/{talismanId}")
    @Operation(operationId = "getTalisman")
    public ApiResponse<TalismanView> get(Authentication authentication, @PathVariable UUID talismanId) {
        return ApiResponse.of(TalismanView.from(service.get(userId(authentication), talismanId)));
    }

    @PostMapping("/{talismanId}/download-url")
    @Operation(operationId = "createTalismanDownloadUrl")
    public ApiResponse<TalismanQueryService.Download> download(Authentication authentication,
            @PathVariable UUID talismanId) {
        return ApiResponse.of(service.download(userId(authentication), talismanId));
    }

    private UUID userId(Authentication authentication) {
        try { return UUID.fromString(authentication.getName()); }
        catch (RuntimeException failure) { throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED); }
    }

    @Schema(requiredProperties = {"items", "hasNext"})
    public record TalismanPage(List<TalismanView> items,
            @Schema(types = {"string", "null"}) String nextCursor, boolean hasNext) {}

    public record TalismanView(UUID id, UUID sourceReadingId, String fortuneType, String element,
            String animal, String phrase, String description, String contentVersion, String status,
            Instant createdAt, Instant readyAt) {
        static TalismanView from(Talisman value) {
            return new TalismanView(value.id(), value.sourceReadingId(), value.fortuneType().name(),
                    value.element().name(), value.animal().name(), value.phrase(), value.description(),
                    value.contentVersion(), value.status().name(), value.createdAt(), value.readyAt());
        }
    }
}
