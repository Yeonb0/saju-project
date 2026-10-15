package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.wallet.purchases-enabled=true", "app.gift.orders-enabled=true",
        "app.gift.purchases-enabled=true", "app.gift.payload-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@ActiveProfiles({"local", "test"})
@AutoConfigureMockMvc
class GiftOpenApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Test void giftContractSeparatesDraftFinancialCompletionAndPendingReturn() throws Exception {
        String json = mvc.perform(get("/v3/api-docs/v1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/gift-quotes'].post.operationId").value("createGiftQuote"))
                .andExpect(jsonPath("$.paths['/api/v1/gift-orders'].post.operationId").value("createGiftOrder"))
                .andExpect(jsonPath("$.paths['/api/v1/gift-orders'].get.operationId").value("listGiftOrders"))
                .andExpect(jsonPath("$.paths['/api/v1/gift-orders/{orderId}/link'].get.operationId").value("getGiftLink"))
                .andExpect(jsonPath("$.paths['/api/v1/gift-orders/{orderId}/link-reissues'].post.operationId").value("reissueGiftLink"))
                .andExpect(jsonPath("$.paths['/api/v1/public/gifts/access'].post.operationId").value("getPublicGiftAccess"))
                .andExpect(jsonPath("$.paths['/api/v1/public/gifts/recipient-profile'].post.operationId").value("submitPublicGiftRecipient"))
                .andExpect(jsonPath("$.paths['/api/v1/gift-orders/{orderId}/purchase'].get.operationId").value("getGiftPurchase"))
                .andExpect(jsonPath("$.paths['/api/v1/gift-orders/{orderId}/purchase'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/gift-orders/{orderId}/purchase'].post.responses['200'].content['application/json'].schema['$ref']").value("#/components/schemas/ApiResponseGiftPurchaseResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/gift-orders/{orderId}/cancel'].post.responses['202']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/gift-orders/{orderId}/cancel'].post.responses['200'].content['application/json'].schema['$ref']").value("#/components/schemas/ApiResponseGiftPurchaseResponse"))
                .andExpect(jsonPath("$.components.schemas.GiftOrderResponse.properties.charged.oneOf[1].type").value("null"))
                .andExpect(jsonPath("$.components.schemas.GiftPurchaseResponse.properties.returnedShells.type").exists())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json).doesNotContain("private_payload", "recipientPhone", "recipientPhoneMasked", "test_gsk_");
        String output = System.getProperty("gift.openapi.output");
        if (output != null) {
            Map<String, Object> schema = mapper.readValue(json, new TypeReference<>() {});
            Files.createDirectories(Path.of(output).getParent());
            Files.writeString(Path.of(output), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sorted(schema)) + "\n", StandardCharsets.UTF_8);
        }
    }
    private Object sorted(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new TreeMap<>(); map.forEach((key, item) -> result.put(key.toString(), sorted(item))); return result;
        }
        if (value instanceof java.util.List<?> list) return list.stream().map(this::sorted).toList();
        return value;
    }
}
