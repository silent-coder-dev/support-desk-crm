package com.support.crm;

import com.support.crm.config.AdminBootstrap;
import com.support.crm.model.User;
import com.support.crm.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

// Same settings as production: signup closed
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:crmprod;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.signup.enabled=false")
class ProductionSettingsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AdminBootstrap adminBootstrap;

    @Test
    void signupIsClosedForBothPageAndForm() throws Exception {
        mockMvc.perform(get("/signup")).andExpect(redirectedUrl("/staff-login"));

        mockMvc.perform(post("/signup").with(csrf())
                        .param("fullname", "Intruder").param("username", "intruder")
                        .param("email", "intruder@example.com").param("mobileNumber", "9876500002")
                        .param("password", "Strong@123"))
                .andExpect(redirectedUrl("/staff-login"));

        assertThat(userRepository.existsByUsername("intruder")).isFalse();
    }

    @Test
    void registerLinksAreHiddenWhileSignupIsClosed() throws Exception {
        mockMvc.perform(get("/staff-login")).andExpect(content().string(not(containsString("Register Account"))));
        mockMvc.perform(get("/")).andExpect(content().string(not(containsString("Agent Registration"))));
    }

    @Test
    void oldDefaultAdminPasswordIsReplacedOnStartup() throws Exception {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        admin.setPassword(passwordEncoder.encode("admin123"));
        userRepository.save(admin);

        adminBootstrap.run();

        String stored = userRepository.findByUsername("admin").orElseThrow().getPassword();
        assertThat(passwordEncoder.matches("admin123", stored)).isFalse();
        assertThat(passwordEncoder.matches("Admin@12345", stored)).isTrue();
    }

    @Test
    void adminPasswordChangedByTheUserIsLeftAlone() throws Exception {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        admin.setPassword(passwordEncoder.encode("MyOwn@Pass1"));
        userRepository.save(admin);

        adminBootstrap.run();

        assertThat(passwordEncoder.matches("MyOwn@Pass1", userRepository.findByUsername("admin").orElseThrow().getPassword())).isTrue();
    }
}
