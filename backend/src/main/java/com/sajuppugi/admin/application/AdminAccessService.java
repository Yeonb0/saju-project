package com.sajuppugi.admin.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AdminAccessService {
    private final JdbcTemplate jdbc;
    private final boolean enabled;
    public AdminAccessService(JdbcTemplate jdbc, @Value("${app.admin.enabled:false}") boolean enabled) { this.jdbc = jdbc; this.enabled = enabled; }
    public boolean allows(Authentication auth, boolean recovery) {
        if (auth == null || !auth.isAuthenticated()) return false;
        try { return permitted(UUID.fromString(auth.getName()), recovery); }
        catch (IllegalArgumentException failure) { return false; }
    }
    public void require(UUID actor, boolean recovery) {
        if (!permitted(actor, recovery)) throw new ApiException(ErrorCode.FORBIDDEN);
    }
    public boolean permitted(UUID actor, boolean recovery) {
        if (!enabled || actor == null) return false;
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM admin_operator_grants g JOIN users u ON u.id = g.user_id
                WHERE g.user_id = ? AND g.active = TRUE AND u.status = 'ACTIVE' AND (? = FALSE OR g.can_recover = TRUE)
                """, Integer.class, actor, recovery);
        return count != null && count == 1;
    }
}
