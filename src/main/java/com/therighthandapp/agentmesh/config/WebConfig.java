package com.therighthandapp.agentmesh.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration to allow AgentMesh-UI frontend to communicate with backend.
 * Enables cross-origin requests from the Next.js development server and production deployments.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final List<String> ALLOWED_ORIGINS = Arrays.asList(
            "http://localhost:3000",           // Next.js dev server
            "http://localhost:3001",           // Alternative dev port
            "http://localhost:13001",          // Docker UI port
            "http://localhost:18082",          // Temporal UI
            "http://127.0.0.1:3000",
            "http://127.0.0.1:3001",
            "http://127.0.0.1:13001",
            "http://127.0.0.1:18082",
            "https://agentmesh-ui.vercel.app", // Production (if deployed)
            "https://agentmesh.io"             // Custom domain (if configured)
    );

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(ALLOWED_ORIGINS.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD")
                .allowedHeaders("*")
                .exposedHeaders("X-Correlation-ID", "X-Tenant-ID")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * CORS configuration bean for Spring's CorsFilter
     * This ensures CORS is applied to all requests including WebSocket handshakes
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Use specific origins when credentials are included
        configuration.setAllowedOrigins(ALLOWED_ORIGINS);

        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"
        ));
        
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(Arrays.asList("X-Correlation-ID", "X-Tenant-ID"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
