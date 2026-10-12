package com.sajuppugi.admin.api;

import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
public class AdminOpenApiConfig {
    @Bean GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder().group("admin").pathsToMatch("/api/v1/admin/**").addOpenApiCustomizer(api -> {
            api.getComponents().addSecuritySchemes("adminSession", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.COOKIE).name("SAJUPPUGI_SESSION"));
            api.getComponents().addSecuritySchemes("adminCsrf", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER).name("X-CSRF-Token"));
            api.setSecurity(List.of(new SecurityRequirement().addList("adminSession")));
            api.getPaths().values().forEach(path -> {
                if (path.getPost() != null) path.getPost().setSecurity(List.of(new SecurityRequirement().addList("adminSession").addList("adminCsrf")));
            });
        }).build();
    }
}
