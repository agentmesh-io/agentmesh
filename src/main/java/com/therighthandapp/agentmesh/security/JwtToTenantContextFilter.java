package com.therighthandapp.agentmesh.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Per-request filter (M13.2) that:
 * <ol>
 *   <li>Reads {@code Authorization: Bearer …}.</li>
 *   <li>Verifies the JWT via {@link AuthenticationService} (signature + expiry +
 *       jti blocklist).</li>
 *   <li>Populates the {@link TenantContext} ThreadLocal AND a Spring
 *       {@link UsernamePasswordAuthenticationToken} with role authorities so
 *       {@code @PreAuthorize("hasRole('admin')")} works.</li>
 *   <li>Always {@link TenantContext#clear()}s in the {@code finally} block.</li>
 * </ol>
 *
 * <p>If no Bearer token is present the filter is a no-op — Spring Security
 * authorisation rules in {@link SecurityConfig} decide whether the request is
 * permitted (e.g. {@code /api/auth/**} is public).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtToTenantContextFilter extends OncePerRequestFilter {

    private final AuthenticationService auth;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        TenantContext ctx = null;
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            var verifyResult = auth.verify(token);
            if (verifyResult.isPresent()) {
                var claims = verifyResult.get();
                String userId = claims.getSubject();
                String tenantId = (String) claims.getClaim("tenant_id");
                String projectId = (String) claims.getClaim("project_id");
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) claims.getClaim("roles");

                ctx = new TenantContext(tenantId, projectId, userId);
                ctx.setRoles(roles == null ? new String[0] : roles.toArray(String[]::new));
                TenantContext.set(ctx);

                List<SimpleGrantedAuthority> auths = (roles == null ? List.<String>of() : roles).stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .collect(Collectors.toList());
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, auths);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        try {
            chain.doFilter(req, res);
        } finally {
            if (ctx != null) TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}

