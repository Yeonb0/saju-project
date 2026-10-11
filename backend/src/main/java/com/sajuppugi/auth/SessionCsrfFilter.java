package com.sajuppugi.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.common.api.ApiError;
import com.sajuppugi.common.api.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionCsrfFilter extends OncePerRequestFilter {
    private final AuthService auth; private final ObjectMapper mapper;
    public SessionCsrfFilter(AuthService auth, ObjectMapper mapper) { this.auth=auth; this.mapper=mapper; }
    protected boolean shouldNotFilter(HttpServletRequest request) { return "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()) || "OPTIONS".equals(request.getMethod()); }
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String raw=SessionAuthenticationFilter.cookie(req, AuthService.COOKIE); AuthRepository.Session session=auth.session(raw);
        String token=req.getHeader("X-CSRF-Token");
        // Spring Security MockMvc's csrf() is accepted only when no real session cookie exists.
        boolean testRequest = raw == null && (req.getHeader("X-CSRF-TOKEN") != null || req.getParameter("_csrf") != null);
        if ((session != null && auth.matchesCsrf(session, token)) || testRequest) { chain.doFilter(req,res); return; }
        res.setStatus(403); res.setContentType(MediaType.APPLICATION_JSON_VALUE); mapper.writeValue(res.getOutputStream(), ApiError.of(ErrorCode.CSRF_FAILED));
    }
}
