package com.sajuppugi.common.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                .build();
    }
}
