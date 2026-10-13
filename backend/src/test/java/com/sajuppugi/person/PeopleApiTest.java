package com.sajuppugi.person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.reading.domain.RelationType;
import com.sajuppugi.fortune.reading.port.ReadingSubjectPort;
import com.sajuppugi.person.application.PersonService;
import com.sajuppugi.person.domain.Person;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PeopleApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PersonService service;
    @Autowired ReadingSubjectPort readingSubjects;
    private final Set<UUID> createdUsers = ConcurrentHashMap.newKeySet();

    @AfterEach
    void removeSyntheticRows() {
        for (UUID userId : createdUsers) {
            jdbc.update("DELETE FROM people WHERE owner_user_id = ?", userId);
            jdbc.update("DELETE FROM person_owner_locks WHERE owner_user_id = ?", userId);
            jdbc.update("DELETE FROM users WHERE id = ?", userId);
        }
        createdUsers.clear();
    }

    @Test
    void createsListsUpdatesAndDeletesOwnedPersonWithoutPlaintextStorage() throws Exception {
        UUID owner = UUID.randomUUID();
        insertActiveUser(owner);

        mvc.perform(post("/api/v1/people").with(user(owner.toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(selfBody("민지")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.profileType").value("SELF"))
                .andExpect(jsonPath("$.data.displayName").value("민지"))
                .andExpect(jsonPath("$.data.birthDate").value("2008-03-12"));

        UUID personId = jdbc.queryForObject("SELECT id FROM people WHERE owner_user_id = ?", UUID.class, owner);
        byte[] encryptedName = jdbc.queryForObject("SELECT display_name_enc FROM people WHERE id = ?", byte[].class, personId);
        assertThat(new String(encryptedName, StandardCharsets.UTF_8)).doesNotContain("민지");
        assertThat(readingSubjects.getOwnedSubject(owner, personId).displayName()).isEqualTo("민지");
        assertThat(readingSubjects.getOwnedSubject(UUID.randomUUID(), personId)).isNull();

        mvc.perform(get("/api/v1/people").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));

        mvc.perform(patch("/api/v1/people/{personId}", personId).with(user(owner.toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody("민지 수정")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("민지 수정"));

        mvc.perform(delete("/api/v1/people/{personId}", personId).with(user(owner.toString())).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/people/{personId}", personId).with(user(owner.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));
        assertThat(readingSubjects.getOwnedSubject(owner, personId)).isNull();
    }

    @Test
    void enforcesSelfOtherConsentOwnershipAndOtherLimit() throws Exception {
        UUID owner = UUID.randomUUID();
        insertActiveUser(owner);
        service.create(owner, Person.ProfileType.SELF, "본인", birth(), null, false);
        assertCode(() -> service.create(owner, Person.ProfileType.SELF, "본인2", birth(), null, false),
                ErrorCode.PRIMARY_PERSON_ALREADY_EXISTS);
        assertCode(() -> service.create(owner, Person.ProfileType.OTHER, "타인", birth(), null, true),
                ErrorCode.RELATION_TYPE_REQUIRED);
        assertCode(() -> service.create(owner, Person.ProfileType.OTHER, "타인", birth(), RelationType.FRIEND, false),
                ErrorCode.INVALID_REQUEST);

        UUID hidden = service.create(owner, Person.ProfileType.OTHER, "친구0", birth(), RelationType.FRIEND, true).id();
        for (int index = 1; index < 10; index++) {
            service.create(owner, Person.ProfileType.OTHER, "친구" + index, birth(), RelationType.FRIEND, true);
        }
        assertCode(() -> service.create(owner, Person.ProfileType.OTHER, "초과", birth(), RelationType.FRIEND, true),
                ErrorCode.PERSON_LIMIT_EXCEEDED);

        mvc.perform(get("/api/v1/people/{personId}", hidden).with(user(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidBirthInputAndUnauthenticatedAccess() throws Exception {
        UUID owner = UUID.randomUUID();
        insertActiveUser(owner);
        mvc.perform(post("/api/v1/people").with(user(owner.toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfBody("민지").replace("\"isLeapMonth\":false", "\"isLeapMonth\":true")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/people")).andExpect(status().isUnauthorized());
    }

    @Test
    void serializesConcurrentSelfCreationForTheSameOwner() throws Exception {
        UUID owner = UUID.randomUUID();
        insertActiveUser(owner);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            Callable<Object> create = () -> {
                start.await();
                try {
                    return service.create(owner, Person.ProfileType.SELF, "본인", birth(), null, false);
                } catch (ApiException exception) {
                    return exception.errorCode();
                }
            };
            var first = executor.submit(create);
            var second = executor.submit(create);
            start.countDown();
            assertThat(java.util.List.of(first.get(), second.get()))
                    .filteredOn(Person.class::isInstance).hasSize(1);
            assertThat(java.util.List.of(first.get(), second.get()))
                    .contains(ErrorCode.PRIMARY_PERSON_ALREADY_EXISTS);
        }
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM people
                WHERE owner_user_id = ? AND profile_type = 'SELF' AND deleted_at IS NULL
                """, Integer.class, owner)).isEqualTo(1);
    }

    @Test
    void neverCreatesMoreThanTenOthersUnderConcurrentRequests() throws Exception {
        UUID owner = UUID.randomUUID();
        insertActiveUser(owner);
        try (var executor = Executors.newFixedThreadPool(12)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var tasks = java.util.stream.IntStream.range(0, 12).<Callable<Object>>mapToObj(index -> () -> {
                start.await();
                try {
                    return service.create(owner, Person.ProfileType.OTHER, "친구" + index, birth(),
                            RelationType.FRIEND, true);
                } catch (ApiException exception) {
                    return exception.errorCode();
                }
            }).toList();
            var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            var results = futures.stream().map(future -> {
                try { return future.get(); } catch (Exception exception) { throw new RuntimeException(exception); }
            }).toList();
            assertThat(results).filteredOn(Person.class::isInstance).hasSize(10);
            assertThat(results).filteredOn(ErrorCode.PERSON_LIMIT_EXCEEDED::equals).hasSize(2);
        }
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM people
                WHERE owner_user_id = ? AND profile_type = 'OTHER' AND deleted_at IS NULL
                """, Integer.class, owner)).isEqualTo(10);
    }

    @Test
    void rejectsPersonCreationForMissingOrInactiveMembers() {
        assertCode(() -> service.create(UUID.randomUUID(), Person.ProfileType.SELF, "본인", birth(), null, false),
                ErrorCode.PERSON_NOT_FOUND);
        UUID pending = UUID.randomUUID();
        insertActiveUser(pending);
        jdbc.update("UPDATE users SET status = 'WITHDRAWAL_PENDING' WHERE id = ?", pending);
        assertCode(() -> service.create(pending, Person.ProfileType.SELF, "본인", birth(), null, false),
                ErrorCode.PERSON_NOT_FOUND);
    }

    private BirthInput birth() {
        return new BirthInput(LocalDate.of(2008, 3, 12), LocalTime.of(13, 25), false,
                BirthInput.CalendarType.SOLAR, false, BirthInput.Gender.FEMALE);
    }

    private void insertActiveUser(UUID id) {
        var now = Timestamp.from(java.time.Instant.now());
        jdbc.update("""
                INSERT INTO users (id, kakao_subject, nickname, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """, id, "person-test-" + id, "테스트", now, now);
        createdUsers.add(id);
    }

    private String selfBody(String name) {
        return """
                {"profileType":"SELF","displayName":"%s","birthDate":"2008-03-12",
                "birthTime":"13:25","birthTimeUnknown":false,"calendarType":"SOLAR",
                "isLeapMonth":false,"gender":"FEMALE","thirdPartyAuthorizationConfirmed":false}
                """.formatted(name);
    }

    private String updateBody(String name) {
        return """
                {"displayName":"%s","birthDate":"2008-03-12","birthTime":"13:25",
                "birthTimeUnknown":false,"calendarType":"SOLAR","isLeapMonth":false,
                "gender":"FEMALE","thirdPartyAuthorizationConfirmed":false}
                """.formatted(name);
    }

    private void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(code));
    }
}
