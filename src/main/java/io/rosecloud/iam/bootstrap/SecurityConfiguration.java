package io.rosecloud.iam.bootstrap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfiguration {

  private final AccessJwtAuthenticationFilter accessJwtAuthenticationFilter;

  SecurityConfiguration(AccessJwtAuthenticationFilter accessJwtAuthenticationFilter) {
    this.accessJwtAuthenticationFilter = accessJwtAuthenticationFilter;
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        // I1 only exposes bootstrap/login JSON endpoints; csrf can return once browser-auth flows exist.
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(
                        "/api/operator/setup/**",
                        "/api/operator/login",
                        "/api/operator/factor-challenge",
                        "/api/sessions/login",
                        "/api/sessions/factor-challenge",
                        "/api/sessions/refresh",
                        "/api/sessions/logout",
                        "/api/invitations/**",
                        "/api/.well-known/jwks.json",
                        "/actuator/health")
                    .permitAll()
                    .requestMatchers("/api/operator/**")
                    .hasRole("OPERATOR")
                    .requestMatchers(HttpMethod.GET, "/api/me/memberships")
                    .hasRole("USER")
                    .requestMatchers(HttpMethod.POST, "/api/me/tenant-context")
                    .hasRole("USER")
                    .requestMatchers("/api/me/factors/**")
                    .hasAnyRole("USER", "TENANT")
                    .requestMatchers("/api/tenants/**", "/api/demo/**")
                    .hasRole("TENANT")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            accessJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
