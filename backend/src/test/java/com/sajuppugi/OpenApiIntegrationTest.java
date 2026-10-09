package com.sajuppugi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "test"})
class OpenApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    @EnabledIfSystemProperty(named = "openapi.export.path", matches = ".+")
    void exportsFrontendContractWithoutStartingAnUnauthenticatedBusinessServer() throws Exception {
        String json = mvc.perform(get("/v3/api-docs/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/quotes/{quoteId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/wallet'].get").exists())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> schema = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        Path output = Path.of(System.getProperty("openapi.export.path"));
        Files.createDirectories(output.getParent());
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sorted(schema)) + "\n",
                StandardCharsets.UTF_8);
    }

    private Object sorted(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new TreeMap<>();
            map.forEach((key, item) -> result.put(key.toString(), sorted(item)));
            return result;
        }
        if (value instanceof java.util.List<?> list) return list.stream().map(this::sorted).toList();
        return value;
    }

    @Test
    void localProfilePublishesOnlyTheVersionedBusinessApi() throws Exception {
        mvc.perform(get("/v3/api-docs/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("뿌기사주 API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.paths['/api/v1/fortune/basic']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/quotes/{quoteId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/wallet'].get").exists())
                .andExpect(jsonPath("$.components.schemas.FundedQuoteResponse.properties.event").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.SuneungQuoteResponse.properties.event").exists())
                .andExpect(jsonPath("$.components.schemas.FundedQuoteResponse.properties.balanceAfter.type")
                        .value(org.hamcrest.Matchers.hasItem("null")))
                .andExpect(jsonPath("$.components.schemas.CategoryQuoteResponse.properties.recommendedTopUp.type")
                        .value(org.hamcrest.Matchers.hasItem("null")))
                .andExpect(jsonPath("$.components.schemas.FundedQuoteResponse.required")
                        .value(org.hamcrest.Matchers.hasItems("charged", "walletBalance", "balanceAfter", "recommendedTopUp")))
                .andExpect(jsonPath("$.components.schemas.BasicSajuRequest.properties.validInput").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/quotes/fortune/overall'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reading-purchases/love'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/readings/wealth'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/readings/compatibility/{readingId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/readings/sinsal/{readingId}'].get").exists())
                .andExpect(jsonPath("$.paths['/actuator/health']").doesNotExist())
                .andExpect(jsonPath("$.components.securitySchemes.sessionCookie.name").value("JSESSIONID"))
                .andExpect(jsonPath("$.components.securitySchemes.csrfHeader.name").value("X-CSRF-Token"));
    }

    @Test
    void localProfilePublishesSwaggerUi() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
