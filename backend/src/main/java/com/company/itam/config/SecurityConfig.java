package com.company.itam.config;

import com.company.itam.auth.security.JwtAccessDeniedHandler;
import com.company.itam.auth.security.JwtAuthenticationEntryPoint;
import com.company.itam.auth.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            JwtAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                    // Public endpoints: đăng nhập và Swagger/OpenAPI nếu có.
                    .requestMatchers("/v1/auth/login", "/api/v1/auth/login").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/v1/auth/me", "/api/v1/auth/me",
                            "/v1/auth/logout", "/api/v1/auth/logout").authenticated()
                    // Document services also enforce transaction scope and report ownership.
                    .requestMatchers(HttpMethod.GET, "/v1/documents/*/download", "/api/v1/documents/*/download")
                            .hasAnyAuthority("ADMIN", "IT_STAFF", "PUR_STAFF", "USER")
                    .requestMatchers(HttpMethod.GET, "/v1/documents", "/api/v1/documents",
                            "/v1/documents/*", "/api/v1/documents/*", "/v1/transactions", "/api/v1/transactions",
                            "/v1/transactions/*", "/api/v1/transactions/*")
                            .hasAnyAuthority("ADMIN", "IT_STAFF", "PUR_STAFF")
                    .requestMatchers(HttpMethod.POST, "/v1/documents", "/api/v1/documents")
                            .hasAnyAuthority("ADMIN", "PUR_STAFF")
                    .requestMatchers(HttpMethod.DELETE, "/v1/documents/*", "/api/v1/documents/*")
                            .hasAnyAuthority("ADMIN", "PUR_STAFF")
                    // Detail access also requires the ownership check in AssetService.
                    .requestMatchers(HttpMethod.GET, "/v1/users/me/assets", "/api/v1/users/me/assets",
                            "/v1/assets/*", "/api/v1/assets/*")
                            .hasAnyAuthority("ADMIN", "IT_STAFF", "USER")
                    .requestMatchers("/v1/**", "/api/v1/**").hasAnyAuthority("ADMIN", "IT_STAFF")
                    .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * BCrypt password encoder với strength 12 cho prototype.
     * Các tài khoản seed trong V8 được hash bằng cùng strength để verify thành công.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
