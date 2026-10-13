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
                .andExpect(jsonPath("$.paths['/api/v1/top-up-orders/{orderId}/confirm'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/top-up-orders'].get.operationId").value("listTopUpOrders"))
                .andExpect(jsonPath("$.paths['/api/v1/daily-fortunes/today/share'].post.operationId").value("createDailyFortuneShare"))
                .andExpect(jsonPath("$.paths['/api/v1/daily-fortune-shares/{token}'].get.operationId").value("getDailyFortuneShare"))
                .andExpect(jsonPath("$.paths['/api/v1/daily-fortune-shares/{shareId}'].delete.operationId").value("revokeDailyFortuneShare"))
                .andExpect(jsonPath("$.paths['/api/v1/me/consents'].get.operationId").value("getMemberConsents"))
                .andExpect(jsonPath("$.paths['/api/v1/me/consents'].post.operationId").value("acceptMemberConsents"))
                .andExpect(jsonPath("$.paths['/api/v1/talismans'].get.operationId").value("listTalismans"))
                .andExpect(jsonPath("$.paths['/api/v1/talismans/{talismanId}'].get.operationId").value("getTalisman"))
                .andExpect(jsonPath("$.paths['/api/v1/talismans/{talismanId}/download-url'].post.operationId")
                        .value("createTalismanDownloadUrl"))
                .andExpect(jsonPath("$.paths['/api/v1/top-up-orders/{orderId}/confirm'].post.responses['409'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiError"))
                .andExpect(jsonPath("$.components.schemas.ApiError.properties.details").exists())
                .andExpect(jsonPath("$.components.schemas.FieldError.properties.field").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products'].get.operationId").value("listTopUpProducts"))
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
    void catalogNullableDisplayFieldsAcceptBothMoneyAndNull() throws Exception {
        String json = mvc.perform(get("/v3/api-docs/v1")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var root = mapper.readTree(json);
        var factory = com.networknt.schema.JsonSchemaFactory.getInstance(
                com.networknt.schema.SpecVersion.VersionFlag.V202012);
        for (String name : java.util.List.of("ProductResponse", "FundedQuoteResponse", "CategoryQuoteResponse", "SuneungQuoteResponse")) {
            var schema = mapper.createObjectNode();
            schema.set("components", root.get("components"));
            schema.set("oneOf", root.path("components").path("schemas").path(name)
                    .path("properties").path("regularPrice").get("oneOf"));
            var validator = factory.getSchema(schema);
            org.assertj.core.api.Assertions.assertThat(validator.validate(mapper.readTree("null"))).isEmpty();
            org.assertj.core.api.Assertions.assertThat(validator.validate(
                    mapper.readTree("{\"currency\":\"TURTLE_SHELL\",\"amount\":20}"))).isEmpty();
            org.assertj.core.api.Assertions.assertThat(validator.validate(mapper.readTree("\"20\""))).isNotEmpty();
        }
        for (String field : java.util.List.of("fortuneType", "option")) {
            var validator = factory.getSchema(root.path("components").path("schemas")
                    .path("ProductResponse").path("properties").path(field));
            org.assertj.core.api.Assertions.assertThat(validator.validate(mapper.readTree("null"))).isEmpty();
            org.assertj.core.api.Assertions.assertThat(validator.validate(mapper.readTree("\"UNKNOWN\""))).isNotEmpty();
        }
    }

    @Test
    void localProfilePublishesSwaggerUi() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
