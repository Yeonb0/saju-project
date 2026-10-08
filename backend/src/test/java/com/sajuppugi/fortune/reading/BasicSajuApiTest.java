package com.sajuppugi.fortune.reading;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BasicSajuApiTest {
    @Autowired MockMvc mvc;

    @Test
    void calculatesBasicSajuForAuthenticatedUser() throws Exception {
        String userId = UUID.randomUUID().toString();

        mvc.perform(post("/api/v1/fortune/basic")
                        .with(user(userId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solarRequest("2004-03-15", "14:32")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pillars.year.display").value("갑신"))
                .andExpect(jsonPath("$.data.pillars.month.display").value("정묘"))
                .andExpect(jsonPath("$.data.pillars.day.display").value("계사"))
                .andExpect(jsonPath("$.data.pillars.hour.display").value("기미"))
                .andExpect(jsonPath("$.data.dayMaster.hangul").value("계"))
                .andExpect(jsonPath("$.data.fiveElements.counts.WOOD").isNumber())
                .andExpect(jsonPath("$.data.calculationVersion").value("manse-2026.10-v1"))
                .andExpect(jsonPath("$.data.birthTimeKnown").value(true))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(userId))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("2004-03-15"))));
    }

    @Test
    void requiresAuthenticatedUserAndCsrf() throws Exception {
        mvc.perform(post("/api/v1/fortune/basic").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(solarRequest("2004-03-15", "14:32")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mvc.perform(post("/api/v1/fortune/basic").with(user(UUID.randomUUID().toString()))
                        .contentType(MediaType.APPLICATION_JSON).content(solarRequest("2004-03-15", "14:32")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_FAILED"));
    }

    @Test
    void supportsKoreanLeapLunarDate() throws Exception {
        mvc.perform(post("/api/v1/fortune/basic")
                        .with(user(UUID.randomUUID().toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "birthDate":"2020-04-15",
                                  "birthTime":"09:00",
                                  "birthTimeUnknown":false,
                                  "calendarType":"LUNAR",
                                  "leapMonth":true,
                                  "gender":"FEMALE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pillars.year.display").isString())
                .andExpect(jsonPath("$.data.pillars.hour.display").isString());
    }

    @Test
    void omitsHourFactsWhenBirthTimeIsUnknown() throws Exception {
        mvc.perform(post("/api/v1/fortune/basic")
                        .with(user(UUID.randomUUID().toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "birthDate":"2004-03-15",
                                  "birthTime":null,
                                  "birthTimeUnknown":true,
                                  "calendarType":"SOLAR",
                                  "leapMonth":false,
                                  "gender":"UNSPECIFIED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pillars.hour").doesNotExist())
                .andExpect(jsonPath("$.data.tenGods.hour").doesNotExist())
                .andExpect(jsonPath("$.data.birthTimeKnown").value(false));
    }

    @Test
    void rejectsUnknownTimeOnSolarTermBoundary() throws Exception {
        mvc.perform(post("/api/v1/fortune/basic")
                        .with(user(UUID.randomUUID().toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "birthDate":"2024-02-04",
                                  "birthTime":null,
                                  "birthTimeUnknown":true,
                                  "calendarType":"SOLAR",
                                  "leapMonth":false,
                                  "gender":"FEMALE"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BIRTH_TIME_REQUIRED_AT_TERM"));
    }

    @Test
    void validatesMalformedAndContradictoryInput() throws Exception {
        mvc.perform(post("/api/v1/fortune/basic")
                        .with(user(UUID.randomUUID().toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solarRequest("2004/03/15", "25:99")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(post("/api/v1/fortune/basic")
                        .with(user(UUID.randomUUID().toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solarRequest("2004-03-15", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CALENDAR_DATE"));
    }

    private String solarRequest(String date, String time) {
        String jsonTime = time == null ? "null" : "\"" + time + "\"";
        return """
                {
                  "birthDate":"%s",
                  "birthTime":%s,
                  "birthTimeUnknown":false,
                  "calendarType":"SOLAR",
                  "leapMonth":false,
                  "gender":"FEMALE"
                }
                """.formatted(date, jsonTime);
    }
}
