package com.sajuppugi.auth;

import com.sajuppugi.common.api.ApiResponse;
import com.sajuppugi.person.port.PersonRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/api/v1")
class AuthController {
    private final AuthService auth;
    private final PersonRepository people;
    AuthController(AuthService auth, PersonRepository people) { this.auth=auth; this.people=people; }
    @GetMapping("/auth/kakao/authorize")
    String authorize(@RequestParam(required=false) String returnTo) { return "redirect:" + auth.start(returnTo); }
    @GetMapping("/auth/kakao/callback")
    String callback(@RequestParam(required=false) String state, @RequestParam(required=false) String code,
                    @RequestParam(required=false) String error, HttpServletRequest request, HttpServletResponse response) {
        if (error != null) {
            String returnTo = auth.abort(state);
            return "redirect:" + auth.loginFailureUrl(returnTo, "access_denied".equals(error));
        }
        AuthService.Login login=auth.complete(state, code,
                SessionAuthenticationFilter.cookie(request, AuthService.COOKIE));
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(AuthService.COOKIE, login.rawToken()).httpOnly(true).secure(auth.isSecure()).sameSite("Lax").path("/").build().toString());
        return "redirect:" + auth.frontendUrl(login.returnTo());
    }
    @GetMapping("/session") @ResponseBody
    ApiResponse<SessionResponse> session(HttpServletRequest request) {
        AuthRepository.Session session=auth.session(SessionAuthenticationFilter.cookie(request, AuthService.COOKIE));
        return ApiResponse.of(session==null ? new SessionResponse(false,null,null,null,null,null) : new SessionResponse(true,session.userId(),session.nickname(),people.activeSelfExists(session.userId()),session.expiresAt(),auth.csrf(session)));
    }
    @PostMapping("/auth/logout") @ResponseBody
    ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(SessionAuthenticationFilter.cookie(request, AuthService.COOKIE));
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(AuthService.COOKIE, "").httpOnly(true).secure(auth.isSecure()).sameSite("Lax").path("/").maxAge(0).build().toString());
        return ApiResponse.of(null);
    }
    record SessionResponse(boolean authenticated, java.util.UUID userId, String nickname, Boolean hasPrimaryPerson,
                           Instant expiresAt, String csrfToken) { }
}
