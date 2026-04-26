package com.therighthandapp.agentmesh.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SpEL-friendly RBAC gate (M13.2) used from {@code @PreAuthorize("@rbac.allow('admin','developer')")}.
 *
 * <p>When {@code agentmesh.security.auth-enforced=false} (default during the
 * sprint, Risk register R6 in {@code docs/PLAN_M13.2.md}), this gate is a
 * no-op so existing UAT/k6 keep passing. When the flag is flipped to true,
 * every annotated endpoint enforces the role list. Roles are case-
 * insensitive and accepted both with and without the {@code ROLE_} prefix.
 */
@Component("rbac")
@Slf4j
public class RbacEnforcer {

    private final boolean enforced;

    public RbacEnforcer(@Value("${agentmesh.security.auth-enforced:false}") boolean enforced) {
        this.enforced = enforced;
        log.info("[security] RBAC enforcer initialised — enforced={}", enforced);
    }

    /**
     * Return {@code true} if the current authenticated principal has any of
     * {@code allowed}. Always returns {@code true} when enforcement is off.
     */
    public boolean allow(String... allowed) {
        if (!enforced) return true;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return false;
        }
        Set<String> have = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(s -> s.startsWith("ROLE_") ? s.substring(5) : s)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        return Arrays.stream(allowed)
                .map(String::toLowerCase)
                .anyMatch(have::contains);
    }

    /** Convenience for read-or-better. */
    public boolean any() { return allow("admin", "developer", "viewer"); }
    /** Convenience for write. */
    public boolean write() { return allow("admin", "developer"); }
    /** Convenience for admin-only. */
    public boolean admin() { return allow("admin"); }
}

