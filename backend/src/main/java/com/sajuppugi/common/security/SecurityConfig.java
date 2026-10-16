package com.sajuppugi.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.common.api.ApiError;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.auth.AuthService;
import com.sajuppugi.auth.SessionAuthenticationFilter;
import com.sajuppugi.auth.SessionCsrfFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper,
            Environment environment, AuthService auth, com.sajuppugi.admin.application.AdminAccessService admin) throws Exception {
        // Authentication and business permissions will be implemented by BE-B.
        http.authorizeHttpRequests(authorize -> {
            authorize.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
            authorize.requestMatchers("/api/v1/auth/kakao/authorize", "/api/v1/auth/kakao/callback", "/api/v1/session").permitAll();
            authorize.requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated();
            authorize.requestMatchers(HttpMethod.GET, "/api/v1/products").permitAll();
            authorize.requestMatchers(HttpMethod.POST, "/api/v1/webhooks/toss").permitAll();
            authorize.requestMatchers(HttpMethod.POST, "/api/v1/public/gifts/access",
                    "/api/v1/public/gifts/recipient-profile", "/api/v1/public/gifts/result",
                    "/api/v1/public/direct-gifts/access").permitAll();
            authorize.requestMatchers(HttpMethod.GET, "/api/v1/daily-fortune-shares/*").permitAll();
            authorize.requestMatchers(HttpMethod.GET, "/api/v1/public/result-shares/*").permitAll();
            authorize.requestMatchers(HttpMethod.DELETE, "/api/v1/result-shares/*").authenticated();
            authorize.requestMatchers(HttpMethod.DELETE, "/api/v1/daily-fortune-shares/*").authenticated();
            authorize.requestMatchers("/api/v1/admin/**").access((authentication, context) ->
                    new org.springframework.security.authorization.AuthorizationDecision(admin.allows(authentication.get(),
                            !java.util.List.of("GET", "HEAD", "OPTIONS").contains(context.getRequest().getMethod()))));
            authorize.requestMatchers("/api/v1/fortune/basic").authenticated();
            authorize.requestMatchers("/api/v1/quotes/{quoteId}", "/api/v1/wallet").authenticated();
            authorize.requestMatchers("/api/v1/top-up-orders", "/api/v1/top-up-orders/**").authenticated();
            authorize.requestMatchers("/api/v1/direct-gift-batches", "/api/v1/direct-gift-batches/**",
                    "/api/v1/direct-payment-batches", "/api/v1/direct-payment-batches/**").authenticated();
            authorize.requestMatchers("/api/v1/gift-quotes", "/api/v1/gift-quotes/**",
                    "/api/v1/gift-orders", "/api/v1/gift-orders/**").authenticated();
            authorize.requestMatchers("/api/v1/quotes/fortune", "/api/v1/quotes/fortune/**",
                    "/api/v1/reading-purchases", "/api/v1/reading-purchases/**",
                    "/api/v1/readings/**", "/api/v1/people", "/api/v1/people/**",
                    "/api/v1/talismans", "/api/v1/talismans/**",
                    "/api/v1/received-gifts", "/api/v1/received-gifts/**",
                    "/api/v1/daily-fortunes/today", "/api/v1/daily-fortunes/today/share",
                    "/api/v1/me", "/api/v1/me/**").authenticated();
            if (environment.acceptsProfiles(Profiles.of("local"))) {
                authorize.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
            }
            authorize.anyRequest().denyAll();
        });
        http.formLogin(form -> form.disable());
        http.httpBasic(basic -> basic.disable());
        http.csrf(csrf -> csrf.disable());
        http.requestCache(cache -> cache.disable());
        SessionAuthenticationFilter sessionAuthenticationFilter = new SessionAuthenticationFilter(auth);
        SessionCsrfFilter sessionCsrfFilter = new SessionCsrfFilter(auth, mapper);
        http.addFilterBefore(sessionAuthenticationFilter, AnonymousAuthenticationFilter.class);
        http.addFilterAfter(sessionCsrfFilter, SessionAuthenticationFilter.class);
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
