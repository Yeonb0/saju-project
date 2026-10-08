package com.sajuppugi.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.common.api.ApiError;
import com.sajuppugi.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper,
            Environment environment) throws Exception {
        // Authentication and business permissions will be implemented by BE-B.
        http.authorizeHttpRequests(authorize -> {
            authorize.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
            authorize.requestMatchers("/api/v1/fortune/basic").authenticated();
            if (environment.acceptsProfiles(Profiles.of("local"))) {
                authorize.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
            }
            authorize.anyRequest().denyAll();
        });
        http.formLogin(form -> form.disable());
        http.httpBasic(basic -> basic.disable());
        http.requestCache(cache -> cache.disable());
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                        writeError(response, mapper, ErrorCode.AUTHENTICATION_REQUIRED))
                .accessDeniedHandler((request, response, exception) ->
                        writeError(response, mapper, exception instanceof CsrfException
                                ? ErrorCode.CSRF_FAILED : ErrorCode.FORBIDDEN)));
        return http.build();
    }

    @Bean
    org.springframework.security.core.userdetails.UserDetailsService userDetailsService() {
        // Prevent Boot's generated development user from becoming an authentication path.
        return username -> { throw new UsernameNotFoundException("Authentication is not configured"); };
    }

    private static void writeError(HttpServletResponse response, ObjectMapper mapper,
            ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), ApiError.of(code));
    }
}
