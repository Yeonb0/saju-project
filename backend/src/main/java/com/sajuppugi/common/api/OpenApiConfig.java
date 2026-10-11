package com.sajuppugi.common.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {
    public static final String SESSION_COOKIE = "sessionCookie";
    public static final String CSRF_HEADER = "csrfHeader";

    @Bean
    OpenAPI sajuppugiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("뿌기사주 API")
                        .description("뿌기사주 백엔드 API 문서")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(SESSION_COOKIE, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("카카오 로그인 후 발급되는 HttpOnly 세션 쿠키"))
                        .addSecuritySchemes(CSRF_HEADER, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-CSRF-Token")
                                .description("세션 기반 상태 변경 요청에 필요한 CSRF 토큰")));
    }

    @Bean
    GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("v1")
                .pathsToMatch("/api/v1/**")
                .addOpenApiCustomizer(OpenApiConfig::catalogNullablePrices)
                .addOpenApiCustomizer(OpenApiConfig::commonErrors)
                .build();
    }

    private static void catalogNullablePrices(OpenAPI api) {
        // A nullable $ref emitted by springdoc is not a union in OpenAPI 3.1.
        for (String name : List.of("ProductResponse", "FundedQuoteResponse", "CategoryQuoteResponse", "SuneungQuoteResponse")) {
            Schema<?> schema = api.getComponents().getSchemas().get(name);
            if (schema != null && schema.getProperties() != null) {
                schema.addProperty("regularPrice", new Schema<>()
                        .oneOf(List.of(new Schema<>().$ref("#/components/schemas/Price"), nullSchema())));
            }
        }
        Schema<?> product = api.getComponents().getSchemas().get("ProductResponse");
        if (product != null) {
            for (String field : List.of("fortuneType", "option")) {
                Schema<?> property = product.getProperties().get(field);
                Schema<Object> value = new Schema<>();
                value.setTypes(Set.of("string"));
                value.setEnum(new java.util.ArrayList<>(property.getEnum()));
                product.addProperty(field, new Schema<>().oneOf(List.of(value, nullSchema())));
            }
        }
    }

    private static Schema<Object> nullSchema() {
        Schema<Object> schema = new Schema<>();
        schema.setTypes(Set.of("null"));
        return schema;
    }

    private static void commonErrors(OpenAPI api) {
        io.swagger.v3.core.converter.ModelConverters.getInstance().readAll(ApiError.class)
                .forEach(api.getComponents()::addSchemas);
        api.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            for (String status : List.of("400", "401", "403", "404", "409", "422", "500", "503")) {
                var content = new io.swagger.v3.oas.models.media.Content().addMediaType("application/json",
                        new io.swagger.v3.oas.models.media.MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError")));
                operation.getResponses().putIfAbsent(status, new io.swagger.v3.oas.models.responses.ApiResponse()
                        .description("Common error envelope; branch on code, not message.").content(content));
            }
        }));
    }
}
