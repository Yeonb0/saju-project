package com.sajuppugi.fortune.reading.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.calculation.domain.BirthInput.CalendarType;
import com.sajuppugi.fortune.calculation.domain.BirthInput.Gender;
import com.sajuppugi.fortune.reading.application.BasicSajuService;
import com.sajuppugi.fortune.reading.application.BasicSajuService.BasicSajuCommand;
import com.sajuppugi.fortune.reading.application.BasicSajuService.BasicSajuResult;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fortune/basic")
public class BasicSajuController {
    private final BasicSajuService service;

    public BasicSajuController(BasicSajuService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<BasicSajuResult> calculate(
            Authentication authentication,
            @Valid @RequestBody BasicSajuRequest request) {
        UUID requesterUserId;
        try {
            requesterUserId = UUID.fromString(authentication.getName());
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        return ApiResponse.of(request.personId() == null
                ? service.calculate(requesterUserId, request.toCommand())
                : service.calculateOwned(requesterUserId, request.personId()));
    }

    @Schema(description = "Supply personId only, or complete raw birth information; the two input forms are exclusive.")
    public record BasicSajuRequest(
            UUID personId,
            @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String birthDate,
            @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d") String birthTime,
            Boolean birthTimeUnknown,
            CalendarType calendarType,
            Boolean leapMonth,
            Gender gender) {

        @AssertTrue(message = "Supply either personId only or complete birth information")
        @JsonIgnore
        @Schema(hidden = true)
        public boolean isValidInput() {
            if (personId != null) {
                return birthDate == null && birthTime == null && birthTimeUnknown == null
                        && calendarType == null && leapMonth == null && gender == null;
            }
            return birthDate != null && !birthDate.isBlank() && birthTimeUnknown != null
                    && calendarType != null && leapMonth != null && gender != null;
        }

        BasicSajuCommand toCommand() {
            return new BasicSajuCommand(birthDate, birthTime, birthTimeUnknown,
                    calendarType, leapMonth, gender);
        }
    }
}
