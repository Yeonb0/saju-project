package com.sajuppugi.gift.api;

import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.gift.application.GiftRecipientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
@RequestMapping(value = "/api/v1/public/gifts", produces = "application/json")
public class PublicGiftRecipientController {
    private final GiftRecipientService recipients;

    public PublicGiftRecipientController(GiftRecipientService recipients) { this.recipients = recipients; }

    @PostMapping(value = "/recipient-profile", consumes = "application/json")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "submitPublicGiftRecipient",
            description = "Stores one encrypted recipient snapshot and a durable fulfillment intent. Replays do not return birth data.")
    public ApiResponse<GiftRecipientService.Submission> submit(
            @RequestHeader("Idempotency-Key") String requestKey, @Valid @RequestBody Request request) {
        return ApiResponse.of(recipients.submit(request.token(), requestKey, new GiftRecipientService.Command(
                request.displayName(), request.birthDate(), request.birthTime(), request.birthTimeUnknown(),
                request.calendarType(), request.leapMonth(), request.gender())));
    }

    @Schema(name = "PublicGiftRecipientRequest", requiredProperties = {"token","displayName","birthDate",
            "birthTimeUnknown","calendarType","leapMonth","gender"})
    public record Request(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
            @NotBlank @Size(max = 50) String displayName,
            @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String birthDate,
            @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d") String birthTime,
            boolean birthTimeUnknown,
            @NotNull BirthInput.CalendarType calendarType,
            boolean leapMonth,
            @NotNull BirthInput.Gender gender) {
        @Override public String toString() { return "PublicGiftRecipientRequest[REDACTED]"; }
    }
}
