package com.sajuppugi.common.idempotency;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import jakarta.validation.Validator;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

@ControllerAdvice
public class PurchaseRequestBindingAdvice extends RequestBodyAdviceAdapter {
    private static final Set<String> CONTROLLERS = Set.of(
            "com.sajuppugi.fortune.reading.api.GeneralReadingCategoryController",
            "com.sajuppugi.fortune.reading.api.SuneungReadingController");
    private static final Set<String> PATHS = Set.of("/api/v1/reading-purchases",
            "/api/v1/reading-purchases/overall", "/api/v1/reading-purchases/love",
            "/api/v1/reading-purchases/wealth", "/api/v1/reading-purchases/compatibility",
            "/api/v1/reading-purchases/sinsal");

    private final PurchaseRequestBindingService service;
    private final Validator validator;

    public PurchaseRequestBindingAdvice(PurchaseRequestBindingService service, Validator validator) {
        this.service = service;
        this.validator = validator;
    }

    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return CONTROLLERS.contains(parameter.getContainingClass().getName())
                && parameter.getMethod() != null && parameter.getMethod().getName().startsWith("purchase");
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter,
            Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) return body;
        var request = servlet.getRequest();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!"POST".equals(request.getMethod()) || !PATHS.contains(path)) return body;
        // Let MVC reject invalid DTOs without reserving a key for an unprocessable request.
        if (!validator.validate(body).isEmpty()) return body;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        UUID actor;
        try {
            actor = UUID.fromString(auth.getName());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        service.bind(actor, request.getHeader("Idempotency-Key"), "POST " + path, body);
        return body;
    }
}
