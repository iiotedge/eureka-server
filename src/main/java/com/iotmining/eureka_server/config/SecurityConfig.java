package com.iotmining.eureka_server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Reconciles two earlier, disagreeing drafts of this class: one secured
 * everything (dashboard included) behind form-login-or-basic-auth; the
 * other only required auth on /eureka/** (the client registration API),
 * leaving the dashboard and actuator wide open with a comment flagging
 * that choice as unresolved ("UI can be open or secured as you like").
 *
 * Final answer: both /eureka/** and the dashboard UI require auth. An open
 * registry dashboard hands anyone who can reach this port the platform's
 * entire internal topology - every registered service's name, IP, port,
 * and instance count - which is exactly the kind of reconnaissance
 * information a real production deployment shouldn't expose for free.
 * Only /actuator/health (for an external health check/orchestrator that
 * has no business holding credentials) and /error (needed for error
 * responses to render at all) stay open.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Eureka's own clients (and this dashboard) don't do CSRF tokens.
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
