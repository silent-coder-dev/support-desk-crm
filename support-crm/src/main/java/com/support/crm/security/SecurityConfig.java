package com.support.crm.security;

import com.support.crm.model.User;
import com.support.crm.repository.UserRepository;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

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

            return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                    .password(user.getPassword())
                    .authorities(new SimpleGrantedAuthority(authorityName))
                    .disabled(user.isDisabled())
                    .build();
        };
    }

    // Keeps track of who is signed in, so disabling someone can end their open sessions straight away
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    // Tells the registry when a session ends, so it doesn't grow forever
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    // CSRF protection is on by default: th:action forms get the token automatically
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Lets an SSE response finish cleanly; the first request was already checked
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()

                        // 1. Static assets, auth endpoints, error page, public customer portal & health check
                        .requestMatchers(
                                "/",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/login",
                                "/admin-login",
                                "/staff-login",
                                "/signup",
                                "/error",
                                "/portal/**",
                                "/actuator/health"
                        ).permitAll()

                        // 2. Admin dashboard
                        .requestMatchers("/tickets/admin/**", "/admin/**").hasRole("ADMIN")

                        // 3. Agent operations, including the live stream
                        .requestMatchers("/tickets/**", "/api/**").hasAnyRole("AGENT", "ADMIN")

                        // 4. Everything else (like /profile) needs a login
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/staff-login")
                        .loginProcessingUrl("/login")
                        .successHandler(customAuthenticationSuccessHandler)
                        .failureHandler((request, response, ex) -> response.sendRedirect(
                                ("admin".equals(request.getParameter("portal")) ? "/admin-login" : "/staff-login") + "?error=true"))
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                            response.sendRedirect((isAdmin ? "/admin-login" : "/staff-login") + "?logout=true");
                        })
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                // -1 means no limit on devices. The registry is what lets StaffService end a session.
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry())
                        .expiredUrl("/staff-login?expired=true")
                )
                // REST clients get a plain 401; everyone else goes to the HTML login page
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/admin-login"),
                                PathPatternRequestMatcher.withDefaults().matcher("/admin/**"))
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/admin-login"),
                                PathPatternRequestMatcher.withDefaults().matcher("/tickets/admin/**"))
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/staff-login"),
                                AnyRequestMatcher.INSTANCE)
                );

        return http.build();
    }
}
