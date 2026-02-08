package com.singh.telepathyapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * Global CORS Configuration
 *
 * This fixes the CORS error when allowCredentials is true.
 * Instead of using "*" for origins, we use allowedOriginPatterns which supports wildcards
 * and works with credentials.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // Allow credentials (cookies, authorization headers, etc.)
        config.setAllowCredentials(true);

        // IMPORTANT: Use setAllowedOriginPatterns instead of setAllowedOrigins when allowCredentials is true
        // For development: allow all origins
        config.setAllowedOriginPatterns(Arrays.asList("*"));

        // For production, replace with specific origins:
        // config.setAllowedOriginPatterns(Arrays.asList(
        //     "https://yourdomain.com",
        //     "https://*.yourdomain.com",
        //     "http://localhost:3000",
        //     "http://localhost:5173"
        // ));

        // Allow all headers
        config.setAllowedHeaders(Arrays.asList(
                "Origin",
                "Content-Type",
                "Accept",
                "Authorization",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "X-Requested-With"
        ));

        // Allow all HTTP methods
        config.setAllowedMethods(Arrays.asList(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        // Expose headers that the client can access
        config.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Access-Control-Allow-Origin",
                "Access-Control-Allow-Credentials"
        ));

        // How long the response from a pre-flight request can be cached (in seconds)
        config.setMaxAge(3600L);

        // Apply CORS configuration to all endpoints
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}