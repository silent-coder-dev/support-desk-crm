package com.support.crm.security;

import com.support.crm.model.User;
import com.support.crm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collections;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> {
            User user = userRepository.findByUsername(username.trim().toLowerCase())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

            String rawRole = user.getRole();
            String authorityName = rawRole.startsWith("ROLE_") ? rawRole : "ROLE_" + rawRole;

            return new org.springframework.security.core.userdetails.User(
                    user.getUsername(),
                    user.getPassword(),
                    Collections.singletonList(new SimpleGrantedAuthority(authorityName))
            );
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // 1. Static assets, uploaded avatars, auth endpoints, error page, public customer portal & SSE stream
                        .requestMatchers(
                                "/",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/uploads/**",
                                "/login",
                                "/signup",
                                "/error",
                                "/portal/**"
                        ).permitAll()
                        .requestMatchers("/tickets/live-stream").permitAll()

                        // 2. Profile workspace (all authenticated operators)
                        .requestMatchers("/profile/**").authenticated()

                        // 3. Admin dashboard
                        .requestMatchers("/tickets/admin/**", "/admin/**").hasAnyAuthority("ROLE_ADMIN", "ADMIN")

                        // 4. Agent operations
                        .requestMatchers("/tickets/**").hasAnyAuthority("ROLE_AGENT", "AGENT", "ROLE_ADMIN", "ADMIN")

                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(customAuthenticationSuccessHandler)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/error?status=403")
                );

        return http.build();
    }

    @Bean
    public CommandLineRunner initDefaultAdmin(UserRepository userRepository, PasswordEncoder encoder) {
        return args -> {
            if (userRepository.findByUsername("admin").isEmpty()) {
                userRepository.save(User.builder()
                        .fullname("Executive Administrator")
                        .username("admin")
                        .email("admin@supportcrm.com")
                        .mobileNumber("1234567890")
                        .password(encoder.encode("admin123"))
                        .role("ROLE_ADMIN")
                        .build());
                System.out.println(">>> Default Admin Ready (admin / admin123) <<<");
            }
        };
    }
}