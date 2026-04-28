package com.therighthandapp.agentmesh.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration (M13.2 — AuthN/Z sprint).
 *
 * <p>Two profiles — selected by {@code agentmesh.security.auth-enforced}:
 * <ul>
 *   <li><b>false</b> (default during sprint): all requests permitted; the
 *       {@link JwtToTenantContextFilter} still runs so any incoming Bearer
 *       token populates {@link TenantContext} for downstream services. This
 *       keeps existing UAT/k6 (Risk register R6) green while the rest of the
 *       sprint lands.</li>
 *   <li><b>true</b>: authenticated() required for every endpoint except
 *       {@code /api/auth/**}, {@code /actuator/health}, {@code /actuator/info},
 *       {@code /ws/**} (WS auth handled by HandshakeInterceptor — commit 7/8),
 *       and OpenAPI/swagger.</li>
 * </ul>
 *
 * <p>{@link EnableMethodSecurity} unlocks {@code @PreAuthorize} on controllers
 * (RBAC, commit 6/8). CORS bridges to existing {@code WebConfig} origins so
 * the UI on {@code app.agentmesh.localhost} can call
 * {@code api.agentmesh.localhost} with cookies.
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final JwtToTenantContextFilter jwtFilter;

    /**
     * Hard-default flipped to {@code true} as part of M13.3 commit 1 (R6 close).
     * The YAML default in {@code application.yml} is also {@code true}; this in-code
     * fallback ensures that a deployment which forgets to mount {@code application.yml}
     * still fails closed instead of silently permit-all. To roll back locally for
     * debugging, set {@code AUTH_ENFORCED=false} in the environment.
     */
    @Value("${agentmesh.security.auth-enforced:true}")
    private boolean authEnforced;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("[security] auth-enforced={} — wiring SecurityFilterChain (R6 closed in M13.3 commit 1)", authEnforced);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        if (authEnforced) {
            http.authorizeHttpRequests(authz -> authz
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(
                            "/api/auth/**",
                            "/actuator/health",
                            "/actuator/health/**",
                            "/actuator/info",
                            "/actuator/prometheus",
                            "/ws/**",
                            "/v3/api-docs/**",
                            "/swagger-ui.html",
                            "/swagger-ui/**"
                    ).permitAll()
                    .anyRequest().authenticated()
            );
        } else {
            http.authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
        }

        // OAuth2 resource-server hook is optional here — we already validate via
        // JwtToTenantContextFilter using our own Nimbus parser. Keeping it off
        // avoids double-validation conflicts with the local IdP setup.

        http.httpBasic(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.exceptionHandling(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://app.localhost",
                "http://app.agentmesh.localhost",
                "http://*.agentmesh.localhost"
        ));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Tenant-Id",
                "X-Requested-With", "X-Correlation-Id"
        ));
        cfg.setExposedHeaders(List.of(
                "X-User-Id", "X-Tenant-Id", "X-Roles", "X-Correlation-Id"
        ));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}

