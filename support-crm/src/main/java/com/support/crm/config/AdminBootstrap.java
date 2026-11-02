package com.support.crm.config;

import com.support.crm.model.User;
import com.support.crm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements CommandLineRunner {

    private static final String ADMIN_USERNAME = "admin";

    // Older versions created the admin with this password, and it is public in the git history
    private static final String OLD_DEFAULT_PASSWORD = "admin123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.admin.email:admin@supportcrm.com}")
    private String adminEmail;

    @Override
    public void run(String... args) {
        userRepository.findByUsername(ADMIN_USERNAME).ifPresentOrElse(this::replaceOldPassword, this::createAdmin);
    }

    private void createAdmin() {
        if (adminPassword.isBlank()) {
            log.warn("No admin account exists. Set ADMIN_PASSWORD and restart to create one.");
            return;
        }
        userRepository.save(User.builder()
                .fullname("Administrator")
                .username(ADMIN_USERNAME)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role("ROLE_ADMIN")
                .build());
        log.info("Admin account created with username '{}'", ADMIN_USERNAME);
    }

    private void replaceOldPassword(User admin) {
        if (!passwordEncoder.matches(OLD_DEFAULT_PASSWORD, admin.getPassword())) {
            return;
        }
        if (adminPassword.isBlank()) {
            log.error("SECURITY: admin still uses the old public default password. Set ADMIN_PASSWORD and restart.");
            return;
        }
        admin.setPassword(passwordEncoder.encode(adminPassword));
        userRepository.save(admin);
        log.info("Replaced the old default admin password with ADMIN_PASSWORD");
    }
}
