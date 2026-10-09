package com.sajuppugi.fortune.reading.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.calculation.domain.BirthInput.CalendarType;
import com.sajuppugi.fortune.calculation.domain.BirthInput.Gender;
import com.sajuppugi.fortune.reading.application.BasicSajuService;
import com.sajuppugi.fortune.reading.application.BasicSajuService.BasicSajuCommand;
import com.sajuppugi.fortune.reading.application.BasicSajuService.BasicSajuResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
        return ApiResponse.of(service.calculate(requesterUserId, request.toCommand()));
    }

    public record BasicSajuRequest(
            @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String birthDate,
            @Pattern(regexp = "(?:[01]\\d|2[0-3]):[0-5]\\d") String birthTime,
            @NotNull Boolean birthTimeUnknown,
            @NotNull CalendarType calendarType,
            @NotNull Boolean leapMonth,
            @NotNull Gender gender) {

        BasicSajuCommand toCommand() {
            return new BasicSajuCommand(birthDate, birthTime, birthTimeUnknown,
                    calendarType, leapMonth, gender);
        }
    }
}
