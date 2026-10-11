package com.sajuppugi.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private final AuthService auth;
    public SessionAuthenticationFilter(AuthService auth) { this.auth=auth; }
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String token = cookie(req, AuthService.COOKIE); AuthRepository.Session session=auth.session(token);
        if(session != null) SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(session.userId().toString(), null, java.util.List.of()));
        try { chain.doFilter(req,res); } finally { SecurityContextHolder.clearContext(); }
    }
    public static String cookie(HttpServletRequest request, String name) { return request.getCookies()==null?null:Arrays.stream(request.getCookies()).filter(c->name.equals(c.getName())).map(Cookie::getValue).findFirst().orElse(null); }
}
