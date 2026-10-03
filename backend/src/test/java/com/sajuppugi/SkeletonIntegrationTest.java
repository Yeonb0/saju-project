package com.sajuppugi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.logging.TraceContext;
import com.sajuppugi.common.logging.TraceIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SkeletonIntegrationTest.ProbeConfiguration.class)
class SkeletonIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test
    void applicationStartsWithDatabaseAndFlyway() {
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void healthAndReadinessArePublicWithoutDatabaseDetails() throws Exception {
        for (String path : new String[] {"/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist());
        }
    }

    @Test
    void businessEndpointsAreNotPublicBeforeAuthenticationIsImplemented() throws Exception {
        mvc.perform(get("/api/v1/wallet"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void mutationWithoutCsrfUsesCommonErrorFormat() throws Exception {
        mvc.perform(post("/api/v1/top-up-orders"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_FAILED"))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    void documentationIsNotPublicOutsideLocalProfile() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void responseTraceMatchesHeaderAndDoesNotTrustIncomingTrace() throws Exception {
        var first = mvc.perform(get("/__test/response").header(TraceIdFilter.HEADER, "untrusted-input"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andReturn();
        String trace = first.getResponse().getHeader(TraceIdFilter.HEADER);
        assertThat(trace).isNotBlank().isNotEqualTo("untrusted-input");
        assertThat(first.getResponse().getContentAsString()).contains(trace);
        assertThat(MDC.get(TraceContext.KEY)).isNull();
        var second = mvc.perform(get("/__test/response")).andReturn();
        assertThat(second.getResponse().getHeader(TraceIdFilter.HEADER)).isNotEqualTo(trace);
    }

    @Test
    void validationUsesStableReasonsWithoutReturningInputValues() throws Exception {
        mvc.perform(post("/__test/validate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].reason").value("REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors[0].rejectedValue").doesNotExist());
    }

    @Test
    void malformedJsonUsesCommonErrorFormat() throws Exception {
        mvc.perform(post("/__test/validate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    void businessExceptionUsesDefinedStatusAndCode() throws Exception {
        mvc.perform(get("/__test/business-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void unexpectedExceptionDoesNotExposeMessageOrStackTrace() throws Exception {
        mvc.perform(get("/__test/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("secret-provider-token"))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean ProbeController probeController() {
            return new ProbeController();
        }

        @Bean
        @Order(0)
        SecurityFilterChain probeSecurity(HttpSecurity http) throws Exception {
            return http.securityMatcher("/__test/**")
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .build();
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/__test/response")
        ApiResponse<Map<String, String>> response() {
            return ApiResponse.of(Map.of("status", "READY"));
        }

        @PostMapping("/__test/validate")
        ApiResponse<Map<String, String>> validate(@Valid @RequestBody Input input) {
            return ApiResponse.of(Map.of("status", "READY"));
        }

        @GetMapping("/__test/business-error")
        void businessError() {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @GetMapping("/__test/error")
        void error() {
            throw new IllegalStateException("secret-provider-token");
        }
    }

    record Input(@NotBlank String name) {
    }
}
