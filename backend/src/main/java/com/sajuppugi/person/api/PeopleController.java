package com.sajuppugi.person.api;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.reading.domain.RelationType;
import com.sajuppugi.person.application.PersonService;
import com.sajuppugi.person.domain.Person;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/people")
public class PeopleController {
    private final PersonService service;

    public PeopleController(PersonService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PeopleResponse> list(Authentication authentication) {
        return ApiResponse.of(new PeopleResponse(service.list(userId(authentication)).stream().map(PersonResponse::from).toList()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PersonResponse>> create(Authentication authentication,
                                                               @Valid @RequestBody CreatePersonRequest request) {
        Person created = service.create(userId(authentication), request.profileType(), request.displayName(),
                request.birthInput(), request.relationType(), request.thirdPartyAuthorizationConfirmed());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(PersonResponse.from(created)));
    }

    @GetMapping("/{personId}")
    public ApiResponse<PersonResponse> get(Authentication authentication, @PathVariable UUID personId) {
        return ApiResponse.of(PersonResponse.from(service.get(userId(authentication), personId)));
    }

    @PatchMapping("/{personId}")
    public ApiResponse<PersonResponse> update(Authentication authentication, @PathVariable UUID personId,
                                               @Valid @RequestBody UpdatePersonRequest request) {
        return ApiResponse.of(PersonResponse.from(service.update(userId(authentication), personId,
                request.displayName(), request.birthInput(), request.relationType(),
                request.thirdPartyAuthorizationConfirmed())));
    }

    @DeleteMapping("/{personId}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID personId) {
        service.delete(userId(authentication), personId);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
    }

    public record CreatePersonRequest(@NotNull Person.ProfileType profileType,
                                      @NotBlank @Size(max = 50) String displayName,
                                      @NotNull LocalDate birthDate, LocalTime birthTime,
                                      boolean birthTimeUnknown, @NotNull BirthInput.CalendarType calendarType,
                                      boolean isLeapMonth, @NotNull BirthInput.Gender gender,
                                      RelationType relationType, boolean thirdPartyAuthorizationConfirmed) {
        BirthInput birthInput() {
            return new BirthInput(birthDate, birthTime, birthTimeUnknown, calendarType, isLeapMonth, gender);
        }
    }

    public record UpdatePersonRequest(@NotBlank @Size(max = 50) String displayName,
                                      @NotNull LocalDate birthDate, LocalTime birthTime,
                                      boolean birthTimeUnknown, @NotNull BirthInput.CalendarType calendarType,
                                      boolean isLeapMonth, @NotNull BirthInput.Gender gender,
                                      RelationType relationType, boolean thirdPartyAuthorizationConfirmed) {
        BirthInput birthInput() {
            return new BirthInput(birthDate, birthTime, birthTimeUnknown, calendarType, isLeapMonth, gender);
        }
    }

    public record PeopleResponse(List<PersonResponse> items) {}

    public record PersonResponse(UUID id, String profileType, String displayName, LocalDate birthDate,
                                 LocalTime birthTime, boolean birthTimeUnknown, String calendarType,
                                 boolean isLeapMonth, String gender, String relationType) {
        static PersonResponse from(Person person) {
            BirthInput birth = person.birthInput();
            return new PersonResponse(person.id(), person.profileType().name(), person.displayName(), birth.birthDate(),
                    birth.birthTime(), birth.birthTimeUnknown(), birth.calendarType().name(), birth.leapMonth(),
                    birth.gender().name(), person.relationType() == null ? null : person.relationType().name());
        }
    }
}
