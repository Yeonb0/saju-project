package com.sajuppugi.person.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.reading.domain.RelationType;
import com.sajuppugi.member.domain.Member;
import com.sajuppugi.member.port.MemberRepository;
import com.sajuppugi.member.application.MemberConsentService;
import com.sajuppugi.person.domain.Person;
import com.sajuppugi.person.port.PersonRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonService {
    private static final LocalDate MIN_BIRTH_DATE = LocalDate.of(1900, 1, 1);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final PersonRepository repository;
    private final MemberRepository members;
    private final MemberConsentService consents;
    private final Clock clock;

    public PersonService(PersonRepository repository, MemberRepository members,
                         MemberConsentService consents, Clock clock) {
        this.repository = repository;
        this.members = members;
        this.consents = consents;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Person> list(UUID ownerUserId) {
        return repository.findActiveByOwner(required(ownerUserId));
    }

    @Transactional(readOnly = true)
    public Person get(UUID ownerUserId, UUID personId) {
        return repository.findActiveOwned(required(ownerUserId), required(personId))
                .orElseThrow(() -> new ApiException(ErrorCode.PERSON_NOT_FOUND));
    }

    @Transactional
    public Person create(UUID ownerUserId, Person.ProfileType profileType, String displayName,
                         BirthInput birthInput, RelationType relationType, boolean thirdPartyConfirmed) {
        validate(profileType, displayName, birthInput, relationType, thirdPartyConfirmed);
        ownerUserId = required(ownerUserId);
        requireActiveMember(ownerUserId);
        consents.requireCurrentForPersonCreation(ownerUserId);
        Instant now = clock.instant();
        repository.lockOwner(ownerUserId, now);
        if (profileType == Person.ProfileType.SELF && repository.activeSelfExists(ownerUserId)) {
            throw new ApiException(ErrorCode.PRIMARY_PERSON_ALREADY_EXISTS);
        }
        if (profileType == Person.ProfileType.OTHER && repository.countActiveOthers(ownerUserId) >= 10) {
            throw new ApiException(ErrorCode.PERSON_LIMIT_EXCEEDED);
        }
        Person person = new Person(UUID.randomUUID(), ownerUserId, profileType, displayName.trim(), birthInput,
                relationType, profileType == Person.ProfileType.OTHER ? now : null, now, now);
        repository.insert(person);
        return person;
    }

    @Transactional
    public Person update(UUID ownerUserId, UUID personId, String displayName, BirthInput birthInput,
                         RelationType relationType, boolean thirdPartyConfirmed) {
        Person current = get(ownerUserId, personId);
        validate(current.profileType(), displayName, birthInput, relationType, thirdPartyConfirmed);
        Person updated = new Person(current.id(), current.ownerUserId(), current.profileType(), displayName.trim(),
                birthInput, relationType, current.profileType() == Person.ProfileType.OTHER
                ? current.thirdPartyConfirmedAt() : null, current.createdAt(), clock.instant());
        if (!repository.update(updated)) throw new ApiException(ErrorCode.PERSON_NOT_FOUND);
        return updated;
    }

    @Transactional
    public void delete(UUID ownerUserId, UUID personId) {
        if (!repository.softDelete(required(ownerUserId), required(personId), clock.instant())) {
            throw new ApiException(ErrorCode.PERSON_NOT_FOUND);
        }
    }

    private void validate(Person.ProfileType type, String name, BirthInput birth, RelationType relation,
                          boolean thirdPartyConfirmed) {
        if (type == null || name == null || name.isBlank() || name.trim().length() > 50 || birth == null
                || birth.birthDate() == null || birth.calendarType() == null || birth.gender() == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        LocalDate today = LocalDate.now(clock.withZone(KST));
        if (birth.birthDate().isBefore(MIN_BIRTH_DATE) || birth.birthDate().isAfter(today)
                || (birth.calendarType() == BirthInput.CalendarType.SOLAR && birth.leapMonth())
                || birth.birthTimeUnknown() == (birth.birthTime() != null)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        if (type == Person.ProfileType.SELF && (relation != null || thirdPartyConfirmed)
                || type == Person.ProfileType.OTHER && (relation == null || !thirdPartyConfirmed)) {
            throw new ApiException(type == Person.ProfileType.OTHER && relation == null
                    ? ErrorCode.RELATION_TYPE_REQUIRED : ErrorCode.INVALID_REQUEST);
        }
    }

    private static <T> T required(T value) {
        if (value == null) throw new ApiException(ErrorCode.INVALID_REQUEST);
        return value;
    }

    private void requireActiveMember(UUID userId) {
        if (members.find(userId).map(PersonService::isActive).orElse(false)) return;
        // Authentication filters already reject inactive sessions. Keep the person boundary equally strict for
        // direct service callers and hide member existence from this resource API.
        throw new ApiException(ErrorCode.PERSON_NOT_FOUND);
    }

    private static boolean isActive(Member member) {
        return member.status() == Member.Status.ACTIVE;
    }
}
