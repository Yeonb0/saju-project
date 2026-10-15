package com.sajuppugi.fortune.daily;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.person.application.PersonService;
import com.sajuppugi.person.domain.Person;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DailyFortuneApiTest {
    private static final Instant NOW = Instant.parse("2026-10-11T02:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired PersonService people;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void returnsStableZeroToHundredScoresForOwnedPerson() throws Exception {
        UUID owner = UUID.randomUUID();
        insertActiveUser(owner);
        Person person = people.create(owner, Person.ProfileType.SELF, "민지", birth(), null, false);

        String first = mvc.perform(get("/api/v1/daily-fortunes/today")
                        .param("personId", person.id().toString()).with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-10-11"))
                .andExpect(jsonPath("$.data.personId").value(person.id().toString()))
                .andExpect(jsonPath("$.data.calculationVersion").isString())
                .andExpect(jsonPath("$.data.contentVersion").value("daily-2026.10-v5"))
                .andExpect(jsonPath("$.data.content.oneLineFortune").isNotEmpty())
                .andExpect(jsonPath("$.data.content.luckyColor").isNotEmpty())
                .andExpect(jsonPath("$.data.content.luckyItem").isNotEmpty())
                .andExpect(jsonPath("$.data.content.luckyPlace").isNotEmpty())
                .andExpect(jsonPath("$.data.content.todayAction").isNotEmpty())
                .andExpect(jsonPath("$.data.luckyExtras.guardianAnimal.key").isString())
                .andExpect(jsonPath("$.data.luckyExtras.luckyMission.key").isString())
                .andExpect(jsonPath("$.data.luckyExtras.luckyInitial").value(org.hamcrest.Matchers.matchesPattern("[ㄱ-ㅎ]{2}")))
                .andExpect(jsonPath("$.data.luckyExtras.luckySong.key").isString())
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(get("/api/v1/daily-fortunes/today")
                        .param("personId", person.id().toString()).with(user(owner.toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode firstData = mapper.readTree(first).path("data");
        JsonNode secondData = mapper.readTree(second).path("data");
        assertThat(firstData).isEqualTo(secondData);
        firstData.path("scores").properties().forEach(entry ->
                assertThat(entry.getValue().intValue()).isBetween(0, 100));
    }

    @Test
    void hidesOtherUsersAndDeletedPeople() throws Exception {
        UUID owner = UUID.randomUUID();
        insertActiveUser(owner);
        Person person = people.create(owner, Person.ProfileType.SELF, "민지", birth(), null, false);
        mvc.perform(get("/api/v1/daily-fortunes/today").param("personId", person.id().toString())
                        .with(user(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));

        people.delete(owner, person.id());
        mvc.perform(get("/api/v1/daily-fortunes/today").param("personId", person.id().toString())
                        .with(user(owner.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));
    }

    @Test
    void requiresAuthenticationAndPersonId() throws Exception {
        mvc.perform(get("/api/v1/daily-fortunes/today").param("personId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/daily-fortunes/today").with(user(UUID.randomUUID().toString())))
                .andExpect(status().isBadRequest());
    }

    private BirthInput birth() {
        return new BirthInput(LocalDate.of(2008, 3, 12), LocalTime.of(13, 25), false,
                BirthInput.CalendarType.SOLAR, false, BirthInput.Gender.FEMALE);
    }

    private void insertActiveUser(UUID id) {
        Timestamp now = Timestamp.from(NOW);
        jdbc.update("""
                INSERT INTO users (id, kakao_subject, nickname, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                """, id, "daily-test-" + id, "테스트", now, now);
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
