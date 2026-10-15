package com.sajuppugi.gift.api;

import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.gift.application.PublicGiftResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
@RequestMapping(value = "/api/v1/public/gifts", produces = "application/json")
public class PublicGiftResultController {
    private final PublicGiftResultService results;

    public PublicGiftResultController(PublicGiftResultService results) { this.results = results; }

    @PostMapping(value = "/result", consumes = "application/json")
    @Operation(operationId = "getPublicGiftResult",
            description = "Uses the body-only bearer token. Returns 202 while no immutable result snapshot exists; READY records first provision.")
    public ResponseEntity<ApiResponse<PublicGiftResultService.Result>> result(@Valid @RequestBody Request request) {
        var result = results.get(request.token());
        return ResponseEntity.status(result.state() == PublicGiftResultService.State.PROCESSING
                ? HttpStatus.ACCEPTED : HttpStatus.OK).body(ApiResponse.of(result));
    }

    @Schema(name = "PublicGiftResultRequest", requiredProperties = "token")
    public record Request(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {
        @Override public String toString() { return "PublicGiftResultRequest[token=REDACTED]"; }
    }
}
