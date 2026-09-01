package com.smoke.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smoke.common.Result;
import com.smoke.security.ApiAuditFilter;
import com.smoke.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiAuditFilter apiAuditFilter;
    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origins:}")
    private String configuredOrigins;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            objectMapper.writeValue(response.getWriter(), Result.fail(401, "未登录或令牌无效"));
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            objectMapper.writeValue(response.getWriter(), Result.fail(403, "没有操作权限"));
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/system/capabilities", "/api/auth/login", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/telemetry", "/api/heartbeat").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/notifications/**", "/api/broadcasts/**")
                        .hasAnyRole("COMMUNITY_ADMIN", "SYSTEM_ADMIN", "FIREFIGHTER")
                        .requestMatchers(HttpMethod.PUT, "/api/map/devices/*/position")
                        .hasAnyRole("COMMUNITY_ADMIN", "SYSTEM_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/devices/bind").hasAnyRole("COMMUNITY_ADMIN", "SYSTEM_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/devices/*/credentials").hasAnyRole("COMMUNITY_ADMIN", "SYSTEM_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/devices/**").hasAnyRole("COMMUNITY_ADMIN", "SYSTEM_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/devices/**").hasAnyRole("COMMUNITY_ADMIN", "SYSTEM_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/alerts/**").hasAnyRole("COMMUNITY_ADMIN", "SYSTEM_ADMIN", "FIREFIGHTER")
                        .requestMatchers(HttpMethod.POST, "/api/hazards/*/claim", "/api/hazards/*/submit")
                        .hasAnyRole("COMMUNITY_ADMIN", "SYSTEM_ADMIN", "FIREFIGHTER")
                        .requestMatchers(HttpMethod.POST, "/api/hazards/*/review")
                        .hasAnyRole("COMMUNITY_ADMIN", "SYSTEM_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/notifications/*/audit")
                        .hasAnyRole("COMMUNITY_ADMIN", "SYSTEM_ADMIN", "FIREFIGHTER")
                        .requestMatchers(HttpMethod.POST, "/api/vision/simulation/next", "/api/vision/events/*/review")
                        .hasAnyRole("COMMUNITY_ADMIN", "SYSTEM_ADMIN", "FIREFIGHTER")
                        .requestMatchers(HttpMethod.POST, "/api/broadcasts/*/deliver").hasAnyRole("COMMUNITY_ADMIN", "SYSTEM_ADMIN", "FIREFIGHTER")
                        .requestMatchers(HttpMethod.POST, "/api/broadcasts").hasAnyRole("COMMUNITY_ADMIN", "SYSTEM_ADMIN", "FIREFIGHTER")
                        .requestMatchers(HttpMethod.DELETE, "/api/broadcasts/**").hasAnyRole("COMMUNITY_ADMIN", "SYSTEM_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/broadcasts/**").hasAnyRole("COMMUNITY_ADMIN", "SYSTEM_ADMIN")
                        .requestMatchers("/api/users/**").hasRole("SYSTEM_ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(apiAuditFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    FilterRegistrationBean<ApiAuditFilter> apiAuditFilterRegistration(ApiAuditFilter filter) {
        FilterRegistrationBean<ApiAuditFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = java.util.Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Device-Token"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
