package com.sajuppugi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
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

    @Test
    void localProfilePublishesOnlyTheVersionedBusinessApi() throws Exception {
        mvc.perform(get("/v3/api-docs/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("뿌기사주 API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.paths['/api/v1/fortune/basic']").exists())
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
