package com.support.crm;

import com.sun.net.httpserver.HttpServer;
import com.support.crm.dto.CreateTicketRequest;
import com.support.crm.dto.UpdateTicketRequest;
import com.support.crm.model.TicketStatus;
import com.support.crm.model.User;
import com.support.crm.repository.TicketRepository;
import com.support.crm.repository.UserRepository;
import com.support.crm.service.BrevoEmailClient;
import com.support.crm.service.EmailMessage;
import com.support.crm.service.TicketService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Real production limits on the public form, each test from its own IP address
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:crmguard;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "app.limits.ip-per-minute=3", "app.limits.ip-per-hour=10", "app.limits.email-per-hour=5",
        "app.limits.text-per-hour=3", "app.limits.track-per-minute=10"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerProtectionTest {

    private static final AtomicInteger NEXT = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TicketService ticketService;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private BrevoEmailClient emailClient;

    private String freshIp() {
        return "10.0.0." + NEXT.incrementAndGet();
    }

    private org.springframework.test.web.servlet.ResultActions submit(String ip, String name, String email, String subject, String description) throws Exception {
        return mockMvc.perform(post("/portal/inquiry").with(csrf()).with(r -> { r.setRemoteAddr(ip); return r; })
                .param("customer_name", name).param("customer_email", email)
                .param("subject", subject).param("description", description));
    }

    private User staff(String role) {
        String username = "user" + NEXT.incrementAndGet();
        return userRepository.save(User.builder().username(username).fullname("Person " + username)
                .email(username + "@example.com").password(passwordEncoder.encode("Good@Pass1")).role(role).build());
    }

    private String ticket() {
        int n = NEXT.incrementAndGet();
        CreateTicketRequest r = new CreateTicketRequest();
        r.setCustomer_name("C"); r.setCustomer_email("c" + n + "@example.com"); r.setSubject("S" + n); r.setDescription("D" + n);
        return ticketService.createTicket(r).getTicket_id();
    }

    private UpdateTicketRequest update(String ticketId, TicketStatus status, String note) {
        UpdateTicketRequest u = new UpdateTicketRequest();
        u.setStatus(status); u.setNotes(note); u.setVersion(ticketService.getTicketByTicketId(ticketId).getVersion());
        return u;
    }

    // ---------- Abuse on the public form ----------

    @Test
    void onePersonChangingEveryFieldIsStoppedAfterThreeInAMinute() throws Exception {
        String ip = freshIp();
        long before = ticketRepository.count();
        for (int i = 0; i < 3; i++) {
            String id = ip + "x" + i;
            submit(ip, "Name" + id, "u" + id.replace(".", "") + "@example.com", "Subject" + id, "Text" + id).andExpect(status().is3xxRedirection());
        }
        submit(ip, "Other", "other" + NEXT.incrementAndGet() + "@example.com", "Other subject", "Other text")
                .andExpect(status().is(429))
                .andExpect(content().string(containsString("Too many requests")));
        assertThat(ticketRepository.count()).isEqualTo(before + 3);
    }

    @Test
    void sameComplaintUnderDifferentEmailsIsStopped() throws Exception {
        String text = "Same complaint " + NEXT.incrementAndGet();
        for (int i = 0; i < 3; i++) {
            submit(freshIp(), "N" + i, "copy" + NEXT.incrementAndGet() + "@example.com", "Same", text).andExpect(status().is3xxRedirection());
        }
        submit(freshIp(), "N9", "copy" + NEXT.incrementAndGet() + "@example.com", "same", text.toUpperCase() + "  ").andExpect(status().is(429));
    }

    @Test
    void gmailAliasesCountAsOneInbox() throws Exception {
        String name = "alias" + NEXT.incrementAndGet();
        String[] emails = {name + "@gmail.com", name + "+1@gmail.com", name.charAt(0) + "." + name.substring(1) + "@gmail.com", name + "+2@gmail.com", name + "+3@gmail.com"};
        for (int i = 0; i < emails.length; i++) {
            submit(freshIp(), "N", emails[i], "Topic " + name + i, "Body " + i).andExpect(status().is3xxRedirection());
        }
        submit(freshIp(), "N", name + "+4@gmail.com", "Topic last", "Body last").andExpect(status().is(429));
    }

    @Test
    void repeatOfAnOpenTicketReturnsItAndSaysSo() throws Exception {
        String email = "repeat" + NEXT.incrementAndGet() + "@example.com";
        long before = ticketRepository.count();
        String ref = (String) submit(freshIp(), "A", email, "My   problem", "Details here").andReturn().getFlashMap().get("createdRef");

        submit(freshIp(), "Different Name", email.toUpperCase(), "my problem", "DETAILS   here")
                .andExpect(flash().attribute("createdRef", ref))
                .andExpect(flash().attribute("alreadyOpen", true));
        assertThat(ticketRepository.count()).isEqualTo(before + 1);
    }

    @Test
    void sameDetailsAfterTheTicketClosedOpenANewOne() throws Exception {
        String email = "again" + NEXT.incrementAndGet() + "@example.com";
        User agent = staff("ROLE_AGENT");
        String first = (String) submit(freshIp(), "A", email, "Again", "Same text " + email).andReturn().getFlashMap().get("createdRef");
        ticketService.resolveTicket(first, agent.getUsername());

        String second = (String) submit(freshIp(), "A", email, "Again", "Same text " + email).andReturn().getFlashMap().get("createdRef");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void botFillingTheHiddenFieldSavesNothing() throws Exception {
        long before = ticketRepository.count();
        mockMvc.perform(post("/portal/inquiry").with(csrf()).param("customer_name", "Bot").param("customer_email", "bot@example.com")
                        .param("subject", "Buy").param("description", "Spam").param("website", "http://spam.example"))
                .andExpect(redirectedUrl("/portal/inquiry"));
        assertThat(ticketRepository.count()).isEqualTo(before);
    }

    @Test
    void invalidAndBlankInputIsRefused() throws Exception {
        long before = ticketRepository.count();
        submit(freshIp(), "A", "a@b", "S", "D").andExpect(status().isOk()).andExpect(content().string(containsString("Invalid Email")));
        submit(freshIp(), "   ", "a@example.com", "S", "D").andExpect(status().isOk());
        submit(freshIp(), "A", "a@example.com", "   ", "D").andExpect(status().isOk());
        submit(freshIp(), "A", "a@example.com", "S", "   ").andExpect(status().isOk());
        assertThat(ticketRepository.count()).isEqualTo(before);
    }

    // ---------- Separate login pages ----------

    private org.springframework.test.web.servlet.ResultActions login(String portal, String username) throws Exception {
        return mockMvc.perform(post("/login").with(csrf()).param("portal", portal).param("username", username).param("password", "Good@Pass1"));
    }

    @Test
    void eachPersonUsesTheirOwnLoginPage() throws Exception {
        User agent = staff("ROLE_AGENT");
        User admin = staff("ROLE_ADMIN");
        login("staff", agent.getUsername()).andExpect(redirectedUrl("/tickets"));
        login("admin", admin.getUsername()).andExpect(redirectedUrl("/admin/dashboard"));
        login("admin", agent.getUsername()).andExpect(redirectedUrl("/admin-login?error=true"));
        login("staff", admin.getUsername()).andExpect(redirectedUrl("/staff-login?error=true"));
    }

    @Test
    void anonymousAdminPagesGoToAdminLoginAndLandingIsCustomerOnly() throws Exception {
        mockMvc.perform(get("/admin/dashboard")).andExpect(redirectedUrl("/admin-login"));
        mockMvc.perform(get("/tickets")).andExpect(redirectedUrl("/staff-login"));
        mockMvc.perform(get("/login")).andExpect(redirectedUrl("/staff-login"));
        mockMvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("-login"))))
                .andExpect(content().string(containsString("Submit a request")));
    }

    // ---------- One person on a ticket at a time ----------

    @Test
    void anotherStaffMemberCannotChangeATicketInHand() {
        User first = staff("ROLE_AGENT");
        User second = staff("ROLE_AGENT");
        User admin = staff("ROLE_ADMIN");
        String id = ticket();
        ticketService.updateTicket(id, update(id, TicketStatus.IN_PROGRESS, "Mine"), first.getUsername());

        assertThatThrownBy(() -> ticketService.updateTicket(id, update(id, TicketStatus.CLOSED, "Taking over"), second.getUsername()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("being handled");
        assertThatThrownBy(() -> ticketService.resolveTicket(id, second.getUsername())).isInstanceOf(IllegalStateException.class);

        ticketService.updateTicket(id, update(id, TicketStatus.IN_PROGRESS, "Still mine"), first.getUsername());
        ticketService.updateTicket(id, update(id, TicketStatus.CLOSED, null), admin.getUsername());
        assertThat(ticketService.getTicketByTicketId(id).getStatus()).isEqualTo(TicketStatus.CLOSED);
    }

    // ---------- Customer emails ----------

    @Test
    void updateEmailGivesTheStatusButNotWhoOrWhat() {
        User agent = staff("ROLE_AGENT");
        String id = ticket();
        verify(emailClient, timeout(3000).atLeastOnce()).send(any(EmailMessage.class));
        reset(emailClient);

        ticketService.updateTicket(id, update(id, TicketStatus.IN_PROGRESS, "Secret internal finding"), agent.getUsername());

        ArgumentCaptor<EmailMessage> mail = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailClient, timeout(3000).atLeastOnce()).send(mail.capture());
        assertThat(mail.getValue().subject()).contains(id);
        assertThat(mail.getValue().text()).contains("In progress")
                .doesNotContain(agent.getFullname()).doesNotContain("Secret internal finding");
    }

    @Test
    void trackingPageHidesWhichAgentWroteTheNote() throws Exception {
        User agent = staff("ROLE_AGENT");
        String id = ticket();
        ticketService.updateTicket(id, update(id, TicketStatus.IN_PROGRESS, "Looking"), agent.getUsername());
        String email = ticketService.getTicketByTicketId(id).getCustomer_email();

        mockMvc.perform(post("/portal/track").with(csrf()).with(r -> { r.setRemoteAddr(freshIp()); return r; }).param("ref", id).param("email", email))
                .andExpect(content().string(containsString("Support team")))
                .andExpect(content().string(not(containsString(agent.getFullname()))));
    }

    // ---------- Brevo call, against a local fake server ----------

    @Test
    void brevoGetsTheRightRequest() throws Exception {
        AtomicReference<String> key = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            key.set(exchange.getRequestHeaders().getFirst("api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email";
            new BrevoEmailClient(url, "my-key", "from@example.com", "SupportDesk").send(new EmailMessage("to@example.com", "Hello", "Text"));
        } finally {
            server.stop(0);
        }
        assertThat(key.get()).isEqualTo("my-key");
        assertThat(body.get()).contains("from@example.com").contains("to@example.com").contains("Hello").contains("textContent");
    }

    @Test
    void brevoFailureDoesNotBreakAnything() {
        new BrevoEmailClient("http://127.0.0.1:1/none", "key", "from@example.com", "S").send(new EmailMessage("to@example.com", "Hello", "Text"));
        new BrevoEmailClient("http://127.0.0.1:1/none", "", "", "S").send(new EmailMessage("to@example.com", "Hello", "Text"));
    }
}
