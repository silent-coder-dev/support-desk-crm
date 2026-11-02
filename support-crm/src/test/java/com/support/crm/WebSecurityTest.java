package com.support.crm;

import com.support.crm.dto.CreateTicketRequest;
import com.support.crm.dto.TicketDetailResponse;
import com.support.crm.model.User;
import com.support.crm.repository.UserRepository;
import com.support.crm.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import com.support.crm.service.BrevoEmailClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebSecurityTest {

    private static final String AGENT_USERNAME = "webagent";
    private static final String AGENT_PASSWORD = "Agent@12345";

    @Autowired private MockMvc mockMvc;
    @Autowired private TicketService ticketService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private BrevoEmailClient mailSender;

    @BeforeEach
    void createAgent() {
        if (!userRepository.existsByUsername(AGENT_USERNAME)) {
            userRepository.save(User.builder()
                    .username(AGENT_USERNAME)
                    .password(passwordEncoder.encode(AGENT_PASSWORD))
                    .fullname("Web Agent")
                    .role("ROLE_AGENT")
                    .build());
        }
    }

    private String newTicket(String email) {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setCustomer_name("Priya");
        request.setCustomer_email(email);
        request.setSubject("Secret subject " + System.nanoTime());
        request.setDescription("Secret description");
        return ticketService.createTicket(request).getTicket_id();
    }

    // ---------- Who can reach what ----------

    @Test
    void publicPagesOpenWithoutLogin() throws Exception {
        for (String page : new String[]{"/", "/admin-login", "/staff-login", "/signup", "/portal/inquiry", "/portal/track"}) {
            mockMvc.perform(get(page)).andExpect(status().isOk());
        }
    }

    @Test
    void healthCheckIsPublicButOtherActuatorEndpointsAreNot() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/env")).andExpect(status().is3xxRedirection());
    }

    @Test
    void anonymousUserIsSentToLoginForStaffPages() throws Exception {
        for (String page : new String[]{"/tickets", "/profile", "/tickets/new"}) {
            mockMvc.perform(get(page)).andExpect(redirectedUrl("/staff-login"));
        }
    }

    @Test
    void liveStreamNeedsLogin() throws Exception {
        mockMvc.perform(get("/tickets/live-stream")).andExpect(redirectedUrl("/staff-login"));
    }

    @Test
    void apiAnswers401InsteadOfLoginRedirect() throws Exception {
        mockMvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = AGENT_USERNAME, roles = "AGENT")
    void agentCannotOpenAdminPages() throws Exception {
        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isForbidden());
        mockMvc.perform(get("/tickets/admin/all-tickets")).andExpect(status().isForbidden());
    }

    // ---------- CSRF ----------

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/portal/inquiry")
                        .param("customer_name", "A").param("customer_email", "a@example.com")
                        .param("subject", "S").param("description", "D"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = AGENT_USERNAME, roles = "AGENT")
    void apiWriteWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/tickets/" + newTicket("csrf@example.com") + "/resolve"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = AGENT_USERNAME, roles = "AGENT")
    void logoutOverGetDoesNothing() throws Exception {
        mockMvc.perform(get("/logout")).andExpect(status().isNotFound());
        mockMvc.perform(post("/logout").with(csrf())).andExpect(redirectedUrl("/staff-login?logout=true"));
    }

    // ---------- Customer tracking ----------

    @Test
    void trackingShowsTicketOnlyWithTheRightEmail() throws Exception {
        String ticketId = newTicket("owner@example.com");
        String subject = ticketService.getTicketByTicketId(ticketId).getSubject();

        mockMvc.perform(post("/portal/track").with(csrf())
                        .param("ref", ticketId.toLowerCase()).param("email", "OWNER@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(subject)));

        mockMvc.perform(post("/portal/track").with(csrf())
                        .param("ref", ticketId).param("email", "stranger@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(subject))))
                .andExpect(content().string(containsString("No ticket matches")));
    }

    @Test
    void trackingLinkOnlyFillsInTheReferenceAndRevealsNothing() throws Exception {
        String ticketId = newTicket("link@example.com");
        String subject = ticketService.getTicketByTicketId(ticketId).getSubject();

        mockMvc.perform(get("/portal/track").param("ref", ticketId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"" + ticketId + "\"")))
                .andExpect(content().string(not(containsString(subject))));
    }

    @Test
    void invalidInquiryShowsTheValidationMessage() throws Exception {
        mockMvc.perform(post("/portal/inquiry").with(csrf())
                        .param("customer_name", "A").param("customer_email", "not-an-email")
                        .param("subject", "S").param("description", "D"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Invalid Email")));
    }

    @Test
    void oversizedSubjectIsRejectedInsteadOfCrashingTheDatabase() throws Exception {
        mockMvc.perform(post("/portal/inquiry").with(csrf())
                        .param("customer_name", "A").param("customer_email", "a@example.com")
                        .param("subject", "S".repeat(256)).param("description", "D"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("under 255")));
    }

    // ---------- Login and signup ----------

    @Test
    void loginWorksWithCorrectPasswordAndSendsAgentToQueue() throws Exception {
        mockMvc.perform(formLogin().user(AGENT_USERNAME).password(AGENT_PASSWORD))
                .andExpect(redirectedUrl("/tickets"));
        mockMvc.perform(formLogin().user(AGENT_USERNAME).password("wrong"))
                .andExpect(redirectedUrl("/staff-login?error=true"));
    }

    @Test
    void adminFromBootstrapCanLoginWithConfiguredPassword() throws Exception {
        mockMvc.perform(formLogin().user("admin").password("Admin@12345"))
                .andExpect(redirectedUrl("/admin/dashboard"));
        mockMvc.perform(formLogin().user("admin").password("admin123"))
                .andExpect(redirectedUrl("/staff-login?error=true"));
    }

    @Test
    void signupWithMissingFieldsShowsErrorsInsteadOfCrashing() throws Exception {
        mockMvc.perform(post("/signup").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Username is required")));
    }

    @Test
    void signupCreatesAgentNeverAdmin() throws Exception {
        mockMvc.perform(post("/signup").with(csrf())
                        .param("fullname", "New Agent").param("username", "newagent1")
                        .param("email", "newagent1@example.com").param("mobileNumber", "9876500001")
                        .param("password", "Strong@123").param("role", "ROLE_ADMIN"))
                .andExpect(redirectedUrl("/staff-login"));

        org.assertj.core.api.Assertions.assertThat(userRepository.findByUsername("newagent1").orElseThrow().getRole())
                .isEqualTo("ROLE_AGENT");
    }

    // ---------- Every page renders with real data ----------

    @Test
    @WithMockUser(username = AGENT_USERNAME, roles = "AGENT")
    void agentPagesRender() throws Exception {
        String ticketId = newTicket("render@example.com");

        mockMvc.perform(get("/tickets")).andExpect(status().isOk())
                .andExpect(content().string(containsString(ticketId)))
                .andExpect(content().string(containsString("action=\"/logout\"")))
                .andExpect(content().string(containsString("_csrf")));
        mockMvc.perform(get("/tickets/" + ticketId)).andExpect(status().isOk())
                .andExpect(content().string(containsString("the customer can read this")));
        mockMvc.perform(get("/tickets/new")).andExpect(status().isOk());
        mockMvc.perform(get("/profile")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPagesRender() throws Exception {
        String ticketId = newTicket("adminrender@example.com");

        mockMvc.perform(get("/admin/dashboard")).andExpect(status().isOk());
        mockMvc.perform(get("/admin/tickets")).andExpect(status().isOk())
                .andExpect(content().string(containsString(ticketId)));
        mockMvc.perform(get("/admin/tickets/" + ticketId)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAliasKeepsSearchFilter() throws Exception {
        String ticketId = newTicket("alias@example.com");

        mockMvc.perform(get("/tickets/admin/all-tickets").param("search", ticketId)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = AGENT_USERNAME, roles = "AGENT")
    void missingTicketGives404NotServerError() throws Exception {
        mockMvc.perform(get("/tickets/TKT-99999")).andExpect(status().isNotFound());
    }

    // ---------- Agent ticket form ----------

    @Test
    @WithMockUser(username = AGENT_USERNAME, roles = "AGENT")
    void staleFormShowsConflictMessage() throws Exception {
        String ticketId = newTicket("conflict@example.com");
        Long version = ticketService.getTicketByTicketId(ticketId).getVersion();

        mockMvc.perform(post("/tickets/" + ticketId + "/update").with(csrf())
                        .param("status", "IN_PROGRESS").param("version", String.valueOf(version)))
                .andExpect(flash().attribute("toastType", "success"));

        mockMvc.perform(post("/tickets/" + ticketId + "/update").with(csrf())
                        .param("status", "CLOSED").param("version", String.valueOf(version)))
                .andExpect(flash().attribute("toastType", "danger"));
    }

    // ---------- REST API ----------

    @Test
    @WithMockUser(username = AGENT_USERNAME, roles = "AGENT")
    void apiReturnsCleanJsonErrors() throws Exception {
        mockMvc.perform(get("/api/tickets/TKT-99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NotFound"));

        mockMvc.perform(post("/api/tickets").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"customer_email\":\"bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ValidationFailed"));
    }

    @Test
    @WithMockUser(username = AGENT_USERNAME, roles = "AGENT")
    void apiMapsStaleVersionTo409() throws Exception {
        String ticketId = newTicket("api409@example.com");
        TicketDetailResponse ticket = ticketService.getTicketByTicketId(ticketId);
        String body = "{\"status\":\"IN_PROGRESS\",\"notes\":\"x\",\"version\":" + ticket.getVersion() + "}";

        mockMvc.perform(put("/api/tickets/" + ticketId).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(ticket.getVersion() + 1));

        mockMvc.perform(put("/api/tickets/" + ticketId).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @WithMockUser(username = AGENT_USERNAME, roles = "AGENT")
    void apiClaimTwiceGives409() throws Exception {
        String ticketId = newTicket("apiclaim@example.com");

        mockMvc.perform(post("/api/tickets/" + ticketId + "/claim").with(csrf())).andExpect(status().isOk());
        mockMvc.perform(post("/api/tickets/" + ticketId + "/claim").with(csrf())).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = AGENT_USERNAME, roles = "AGENT")
    void apiHasNoWildcardCors() throws Exception {
        mockMvc.perform(get("/api/tickets").header("Origin", "https://evil.example.com"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @WithMockUser(username = AGENT_USERNAME, roles = "AGENT")
    void csrfEndpointGivesAToken() throws Exception {
        mockMvc.perform(get("/api/tickets/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));
    }
}
