package com.sajuppugi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.admin.enabled=true", "app.admin.recovery-enabled=true", "app.admin.recovery-poll-delay=PT1H"})
@ActiveProfiles({"local", "test"})
@AutoConfigureMockMvc
class AdminOpenApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Test void publishesAdminLookupContractWithoutExposingAnOperatorBootstrapApi() throws Exception {
        mvc.perform(get("/v3/api-docs/admin")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/top-up-orders'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/audit-events'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/refund-cases'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/refund-cases/{caseId}/quote'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/refund-cases/{caseId}/execute'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/top-up-orders/{orderId}/reconcile'].post.responses['202']").exists())
                .andExpect(jsonPath("$.components.schemas.AdminRecoveryResponse.required").value(org.hamcrest.Matchers.hasItems("commandId", "orderId", "state", "resultCode", "completedAt")))
                .andExpect(jsonPath("$.paths['/api/v1/admin/operators']").doesNotExist())
                .andExpect(jsonPath("$.components.securitySchemes.adminSession.name").value("SAJUPPUGI_SESSION"));
    }
    @Test @EnabledIfSystemProperty(named = "admin.openapi.output", matches = ".+")
    void exportsBackendOwnedAdminContract() throws Exception {
        String json = mvc.perform(get("/v3/api-docs/admin")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> schema = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        Path output = Path.of(System.getProperty("admin.openapi.output")); Files.createDirectories(output.getParent());
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sorted(schema)) + "\n", StandardCharsets.UTF_8);
    }
    private Object sorted(Object value) {
        if (value instanceof Map<?, ?> map) { var result = new TreeMap<String, Object>(); map.forEach((k, v) -> result.put(k.toString(), sorted(v))); return result; }
        if (value instanceof java.util.List<?> list) return list.stream().map(this::sorted).toList();
        return value;
    }
}
