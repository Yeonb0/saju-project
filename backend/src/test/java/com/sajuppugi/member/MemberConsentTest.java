package com.sajuppugi.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.member.application.MemberConsentService;
import com.sajuppugi.person.application.PersonService;
import com.sajuppugi.person.domain.Person;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.member.consent.required-for-person-creation=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MemberConsentTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberConsentService consents;
    @Autowired PersonService people;

    @Test
    void recordsTheServerPolicyVersionsOnceAndReturnsTheCurrentStatus() throws Exception {
        UUID userId = insertUser();

        mvc.perform(get("/api/v1/me/consents").with(user(userId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.termsVersion").value("2026-10-14"))
                .andExpect(jsonPath("$.data.requiredAccepted").value(false));

        String body = """
                {"ageOver14Confirmed":true,"termsAgreed":true,"privacyAgreed":true,"marketingAgreed":false}
                """;
        mvc.perform(post("/api/v1/me/consents").with(user(userId.toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiredAccepted").value(true))
                .andExpect(jsonPath("$.data.marketingAgreed").value(false));
        mvc.perform(post("/api/v1/me/consents").with(user(userId.toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_consents WHERE user_id = ?",
                Integer.class, userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT terms_version FROM member_consents WHERE user_id = ?",
                String.class, userId)).isEqualTo("2026-10-14");
    }

    @Test
    void rejectsMissingRequiredConsentAndBlocksPrimaryPersonUntilAccepted() throws Exception {
        UUID userId = insertUser();
        String incomplete = """
                {"ageOver14Confirmed":true,"termsAgreed":false,"privacyAgreed":true,"marketingAgreed":false}
                """;

        mvc.perform(post("/api/v1/me/consents").with(user(userId.toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(incomplete))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUIRED_CONSENT_MISSING"));

        assertThatThrownBy(() -> createSelf(userId))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(ErrorCode.REQUIRED_CONSENT_MISSING));
        consents.accept(userId, true, true, true, false);
        assertThat(createSelf(userId).ownerUserId()).isEqualTo(userId);
    }

    private Person createSelf(UUID userId) {
        return people.create(userId, Person.ProfileType.SELF, "동의 테스트",
                new BirthInput(LocalDate.of(2000, 1, 2), LocalTime.of(12, 30), false,
                        BirthInput.CalendarType.SOLAR, false, BirthInput.Gender.FEMALE), null, false);
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-13T06:00:00Z");
        jdbc.update("""
                INSERT INTO users (id, kakao_subject, nickname, status, created_at, updated_at)
                VALUES (?, ?, '동의 테스트', 'ACTIVE', ?, ?)
                """, id, "consent-" + id, Timestamp.from(now), Timestamp.from(now));
        return id;
    }
}
