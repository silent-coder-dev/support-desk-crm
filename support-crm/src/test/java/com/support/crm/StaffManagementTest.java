package com.support.crm;

import com.support.crm.model.User;
import com.support.crm.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.support.crm.service.BrevoEmailClient;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffManagementTest {

    private static final AtomicInteger NEXT = new AtomicInteger(1000);

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SessionRegistry sessionRegistry;

    @MockitoBean private BrevoEmailClient mailSender;

    private String addStaffAndGetPassword(String username, String role) throws Exception {
        Object password = mockMvc.perform(post("/admin/staff").with(csrf())
                        .param("fullname", "Staff " + username).param("username", username)
                        .param("email", username + "@example.com").param("mobileNumber", "")
                        .param("role", role))
                .andExpect(redirectedUrl("/admin/staff"))
                .andReturn().getFlashMap().get("newStaffPassword");
        return (String) password;
    }

    private User existingAgent() {
        String username = "agent" + NEXT.incrementAndGet();
        return userRepository.save(User.builder().username(username).fullname("Agent " + username)
                .email(username + "@example.com").password(passwordEncoder.encode("Old@Pass1")).role("ROLE_AGENT").build());
    }

    // ---------- Who can reach it ----------

    @Test
    void anonymousIsSentToLogin() throws Exception {
        mockMvc.perform(get("/admin/staff")).andExpect(redirectedUrl("/admin-login"));
    }

    @Test
    @WithMockUser(username = "someagent", roles = "AGENT")
    void agentCannotManageStaff() throws Exception {
        mockMvc.perform(get("/admin/staff")).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/staff").with(csrf()).param("fullname", "X").param("username", "hacker")
                        .param("email", "x@example.com").param("role", "ROLE_ADMIN"))
                .andExpect(status().isForbidden());
        assertThat(userRepository.existsByUsername("hacker")).isFalse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void writesNeedCsrfToken() throws Exception {
        User agent = existingAgent();
        mockMvc.perform(post("/admin/staff/" + agent.getId() + "/disable")).andExpect(status().isForbidden());
        assertThat(userRepository.findById(agent.getId()).orElseThrow().isDisabled()).isFalse();
    }

    // ---------- Adding staff ----------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void addedAgentCanSignInWithTheTemporaryPassword() throws Exception {
        String username = "newstaff" + NEXT.incrementAndGet();
        String password = addStaffAndGetPassword(username, "ROLE_AGENT");

        assertThat(password).matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]{8,72}$");
        User saved = userRepository.findByUsername(username).orElseThrow();
        assertThat(saved.getRole()).isEqualTo("ROLE_AGENT");
        assertThat(saved.getMobileNumber()).isNull();
        assertThat(saved.getPassword()).isNotEqualTo(password);

        mockMvc.perform(formLogin().user(username).password(password)).andExpect(redirectedUrl("/tickets"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void temporaryPasswordsAreDifferentEachTime() throws Exception {
        String first = addStaffAndGetPassword("newstaff" + NEXT.incrementAndGet(), "ROLE_AGENT");
        String second = addStaffAndGetPassword("newstaff" + NEXT.incrementAndGet(), "ROLE_AGENT");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void addedAdminGoesToTheDashboard() throws Exception {
        String username = "newadmin" + NEXT.incrementAndGet();
        String password = addStaffAndGetPassword(username, "ROLE_ADMIN");

        mockMvc.perform(formLogin().user(username).password(password)).andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void inventedRoleIsRejected() throws Exception {
        String username = "badrole" + NEXT.incrementAndGet();
        mockMvc.perform(post("/admin/staff").with(csrf()).param("fullname", "X").param("username", username)
                        .param("email", username + "@example.com").param("mobileNumber", "").param("role", "ROLE_SUPERUSER"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("valid role")));
        assertThat(userRepository.existsByUsername(username)).isFalse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void duplicatesShowClearMessages() throws Exception {
        User agent = existingAgent();

        mockMvc.perform(post("/admin/staff").with(csrf()).param("fullname", "X").param("username", agent.getUsername())
                        .param("email", agent.getEmail()).param("mobileNumber", "").param("role", "ROLE_AGENT"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("already taken")))
                .andExpect(content().string(containsString("already registered")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void missingFieldsShowErrorsInsteadOfCrashing() throws Exception {
        mockMvc.perform(post("/admin/staff").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Username is required")));
    }

    // ---------- Disabling ----------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void disabledStaffCannotSignInUntilEnabledAgain() throws Exception {
        User agent = existingAgent();
        mockMvc.perform(formLogin().user(agent.getUsername()).password("Old@Pass1")).andExpect(redirectedUrl("/tickets"));

        mockMvc.perform(post("/admin/staff/" + agent.getId() + "/disable").with(csrf()))
                .andExpect(flash().attribute("toastType", "success"));
        mockMvc.perform(formLogin().user(agent.getUsername()).password("Old@Pass1")).andExpect(redirectedUrl("/staff-login?error=true"));

        mockMvc.perform(post("/admin/staff/" + agent.getId() + "/enable").with(csrf()));
        mockMvc.perform(formLogin().user(agent.getUsername()).password("Old@Pass1")).andExpect(redirectedUrl("/tickets"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void disablingEndsOpenSessions() throws Exception {
        User agent = existingAgent();
        var principal = org.springframework.security.core.userdetails.User.withUsername(agent.getUsername())
                .password("x").roles("AGENT").build();
        String sessionId = "session-" + agent.getId();
        sessionRegistry.registerNewSession(sessionId, principal);

        mockMvc.perform(post("/admin/staff/" + agent.getId() + "/disable").with(csrf()));

        assertThat(sessionRegistry.getSessionInformation(sessionId).isExpired()).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCannotLockThemselvesOut() throws Exception {
        User admin = userRepository.findByUsername("admin").orElseThrow();

        mockMvc.perform(post("/admin/staff/" + admin.getId() + "/disable").with(csrf()))
                .andExpect(flash().attribute("toastType", "danger"));

        assertThat(userRepository.findByUsername("admin").orElseThrow().isDisabled()).isFalse();
    }

    // ---------- Resetting passwords ----------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void resetReplacesThePassword() throws Exception {
        User agent = existingAgent();

        Object newPassword = mockMvc.perform(post("/admin/staff/" + agent.getId() + "/reset-password").with(csrf()))
                .andReturn().getFlashMap().get("newStaffPassword");

        mockMvc.perform(formLogin().user(agent.getUsername()).password("Old@Pass1")).andExpect(redirectedUrl("/staff-login?error=true"));
        mockMvc.perform(formLogin().user(agent.getUsername()).password((String) newPassword)).andExpect(redirectedUrl("/tickets"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCannotResetTheirOwnPasswordHere() throws Exception {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        String before = admin.getPassword();

        mockMvc.perform(post("/admin/staff/" + admin.getId() + "/reset-password").with(csrf()))
                .andExpect(flash().attribute("toastType", "danger"));

        assertThat(userRepository.findByUsername("admin").orElseThrow().getPassword()).isEqualTo(before);
    }

    // ---------- Pages ----------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void staffPagesRender() throws Exception {
        User agent = existingAgent();
        mockMvc.perform(get("/admin/staff")).andExpect(status().isOk())
                .andExpect(content().string(containsString(agent.getUsername())))
                .andExpect(content().string(not(containsString(agent.getPassword()))));
        mockMvc.perform(get("/admin/staff/new")).andExpect(status().isOk());
    }

    // ---------- Profile ----------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminProfileShowsAdminTools() throws Exception {
        mockMvc.perform(get("/profile")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Admin tools")))
                .andExpect(content().string(containsString("Administrator")));
    }

    @Test
    void agentProfileHidesAdminTools() throws Exception {
        User agent = existingAgent();
        mockMvc.perform(get("/profile").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(agent.getUsername()).roles("AGENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Admin tools"))));
    }

    @Test
    void profileDetailsCanBeUpdated() throws Exception {
        User agent = existingAgent();
        String email = "changed" + agent.getId() + "@example.com";

        mockMvc.perform(post("/profile/details").with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(agent.getUsername()).roles("AGENT"))
                        .param("fullname", "New Name").param("email", email).param("mobileNumber", ""))
                .andExpect(flash().attributeExists("detailsSuccess"));

        User saved = userRepository.findById(agent.getId()).orElseThrow();
        assertThat(saved.getFullname()).isEqualTo("New Name");
        assertThat(saved.getEmail()).isEqualTo(email);
    }

    @Test
    void profileRejectsAnotherPersonsEmailButAllowsYourOwn() throws Exception {
        User agent = existingAgent();
        User other = existingAgent();
        var asAgent = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(agent.getUsername()).roles("AGENT");

        mockMvc.perform(post("/profile/details").with(csrf()).with(asAgent)
                        .param("fullname", "X").param("email", other.getEmail()).param("mobileNumber", ""))
                .andExpect(flash().attributeExists("detailsError"));
        assertThat(userRepository.findById(agent.getId()).orElseThrow().getEmail()).isEqualTo(agent.getEmail());

        mockMvc.perform(post("/profile/details").with(csrf()).with(asAgent)
                        .param("fullname", "X").param("email", agent.getEmail()).param("mobileNumber", ""))
                .andExpect(flash().attributeExists("detailsSuccess"));
    }
}
