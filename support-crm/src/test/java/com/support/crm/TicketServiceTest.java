package com.support.crm;

import com.support.crm.dto.CreateTicketRequest;
import com.support.crm.dto.CreateTicketResponse;
import com.support.crm.dto.TicketDetailResponse;
import com.support.crm.dto.UpdateTicketRequest;
import com.support.crm.exception.TicketNotFoundException;
import com.support.crm.model.TicketStatus;
import com.support.crm.model.User;
import com.support.crm.repository.NoteRepository;
import com.support.crm.repository.TicketAuditLogRepository;
import com.support.crm.repository.TicketRepository;
import com.support.crm.repository.UserRepository;
import com.support.crm.service.AdminDashboardService;
import com.support.crm.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import com.support.crm.service.EmailMessage;
import com.support.crm.service.BrevoEmailClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class TicketServiceTest {

    private static final String AGENT = "agentone";

    @Autowired private TicketService ticketService;
    @Autowired private AdminDashboardService adminDashboardService;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private TicketAuditLogRepository auditLogRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean private BrevoEmailClient mailSender;

    @BeforeEach
    void cleanDatabase() {
        auditLogRepository.deleteAll();
        noteRepository.deleteAll();
        ticketRepository.deleteAll();
        userRepository.findByUsername(AGENT).ifPresent(userRepository::delete);
        userRepository.save(User.builder()
                .username(AGENT).password("x").fullname("Agent One").role("ROLE_AGENT").build());
        reset(mailSender);
    }

    private CreateTicketRequest request(String email, String subject, String description) {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setCustomer_name("Aarav");
        request.setCustomer_email(email);
        request.setSubject(subject);
        request.setDescription(description);
        return request;
    }

    private String createTicket() {
        return ticketService.createTicket(request("aarav@example.com", "Login broken", "Cannot log in")).getTicket_id();
    }

    private UpdateTicketRequest update(TicketStatus status, String notes, Long version) {
        UpdateTicketRequest request = new UpdateTicketRequest();
        request.setStatus(status);
        request.setNotes(notes);
        request.setVersion(version);
        return request;
    }

    private long currentVersion(String ticketId) {
        return ticketService.getTicketByTicketId(ticketId).getVersion();
    }

    @Test
    void newTicketGetsReadableIdAndConfirmationEmail() {
        String ticketId = createTicket();

        assertThat(ticketId).matches("TKT-\\d{3,}");

        ArgumentCaptor<EmailMessage> mail = ArgumentCaptor.forClass(EmailMessage.class);
        verify(mailSender, timeout(3000)).send(mail.capture());
        assertThat(mail.getValue().to()).isEqualTo("aarav@example.com");
        assertThat(mail.getValue().text())
                .contains("https://crm.example.com/portal/track?ref=" + ticketId)
                .doesNotContain("localhost");
    }

    @Test
    void identicalSubmissionReturnsSameTicketAndSendsNoSecondEmail() {
        String first = createTicket();
        verify(mailSender, timeout(3000)).send(any(EmailMessage.class));

        String second = ticketService.createTicket(request("AARAV@example.com", "login BROKEN", "cannot log in")).getTicket_id();

        assertThat(second).isEqualTo(first);
        assertThat(ticketRepository.count()).isEqualTo(1);
        verify(mailSender, after(300).times(1)).send(any(EmailMessage.class));
    }

    @Test
    void sameSubjectWithNewDetailsBecomesFollowUpNote() {
        String first = createTicket();

        String second = ticketService.createTicket(request("aarav@example.com", "Login broken", "Now it shows error 500")).getTicket_id();

        assertThat(second).isEqualTo(first);
        TicketDetailResponse ticket = ticketService.getTicketByTicketId(first);
        assertThat(ticket.getNotes()).hasSize(1);
        assertThat(ticket.getNotes().getFirst().getNote_text()).contains("Now it shows error 500");
    }

    @Test
    void concurrentCreatesAllSucceedWithUniqueIds() throws Exception {
        int count = 12;
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Callable<CreateTicketResponse>> tasks = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String email = "customer" + i + "@example.com";
                tasks.add(() -> ticketService.createTicket(request(email, "Issue", "Details")));
            }

            Set<String> ids = new HashSet<>();
            for (Future<CreateTicketResponse> result : pool.invokeAll(tasks)) {
                ids.add(result.get().getTicket_id());
            }
            assertThat(ids).hasSize(count);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void updateReturnsTheNewVersionSoNextSaveDoesNotFalselyConflict() {
        String ticketId = createTicket();

        Map<String, Object> first = ticketService.updateTicket(ticketId, update(TicketStatus.IN_PROGRESS, "Looking", currentVersion(ticketId)), AGENT);

        assertThat(first.get("version")).isEqualTo(currentVersion(ticketId));

        // Saving again with the version we were just given must work
        ticketService.updateTicket(ticketId, update(TicketStatus.IN_PROGRESS, "Still looking", (Long) first.get("version")), AGENT);
        assertThat(ticketService.getTicketByTicketId(ticketId).getNotes()).hasSize(2);
    }

    @Test
    void staleVersionIsRejectedAndChangesNothing() {
        String ticketId = createTicket();
        long staleVersion = currentVersion(ticketId);

        ticketService.updateTicket(ticketId, update(TicketStatus.CLOSED, "Refund done", staleVersion), AGENT);

        assertThatThrownBy(() -> ticketService.updateTicket(ticketId, update(TicketStatus.IN_PROGRESS, "Late note", staleVersion), AGENT))
                .isInstanceOf(OptimisticLockingFailureException.class);

        TicketDetailResponse ticket = ticketService.getTicketByTicketId(ticketId);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.CLOSED);
        assertThat(ticket.getNotes()).hasSize(1);
    }

    @Test
    void firstUpdateAutoClaimsTheTicket() {
        String ticketId = createTicket();

        ticketService.updateTicket(ticketId, update(TicketStatus.IN_PROGRESS, null, currentVersion(ticketId)), AGENT);

        assertThat(ticketRepository.findByTicketId(ticketId).orElseThrow().getAssignedAgentName()).isEqualTo("Agent One");
    }

    @Test
    void closingTicketEmailsCustomerOnlyOnce() {
        String ticketId = createTicket();
        verify(mailSender, timeout(3000)).send(any(EmailMessage.class));
        reset(mailSender);

        ticketService.updateTicket(ticketId, update(TicketStatus.CLOSED, "Fixed", currentVersion(ticketId)), AGENT);
        ArgumentCaptor<EmailMessage> mail = ArgumentCaptor.forClass(EmailMessage.class);
        verify(mailSender, timeout(3000)).send(mail.capture());
        assertThat(mail.getValue().subject()).contains("Resolved").contains(ticketId);

        // Saving a closed ticket again, or resolving it again, must not email twice
        ticketService.updateTicket(ticketId, update(TicketStatus.CLOSED, null, currentVersion(ticketId)), AGENT);
        ticketService.resolveTicket(ticketId, AGENT);
        verify(mailSender, after(300).times(1)).send(any(EmailMessage.class));
    }

    @Test
    void reopeningClearsResolvedTime() {
        String ticketId = createTicket();
        ticketService.updateTicket(ticketId, update(TicketStatus.CLOSED, null, currentVersion(ticketId)), AGENT);
        assertThat(ticketRepository.findByTicketId(ticketId).orElseThrow().getResolvedAt()).isNotNull();

        ticketService.updateTicket(ticketId, update(TicketStatus.OPEN, null, currentVersion(ticketId)), AGENT);
        assertThat(ticketRepository.findByTicketId(ticketId).orElseThrow().getResolvedAt()).isNull();
    }

    @Test
    void ticketCanOnlyBeClaimedOnce() {
        String ticketId = createTicket();
        ticketService.claimTicket(ticketId, AGENT);

        assertThat(ticketService.getTicketByTicketId(ticketId).getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThatThrownBy(() -> ticketService.claimTicket(ticketId, AGENT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Agent One");
    }

    @Test
    void closedTicketCannotBeClaimed() {
        String ticketId = createTicket();
        ticketService.updateTicket(ticketId, update(TicketStatus.RESOLVED, null, currentVersion(ticketId)), AGENT);
        ticketRepository.findByTicketId(ticketId).ifPresent(t -> {
            t.setAssignedAgentId(null);
            ticketRepository.save(t);
        });

        assertThatThrownBy(() -> ticketService.claimTicket(ticketId, AGENT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownTicketThrowsNotFound() {
        assertThatThrownBy(() -> ticketService.getTicketByTicketId("TKT-999"))
                .isInstanceOf(TicketNotFoundException.class);
    }

    @Test
    void customerLookupNeedsTheMatchingEmail() {
        String ticketId = createTicket();

        assertThat(ticketService.findTicketForCustomer(ticketId, "AARAV@example.com")).isPresent();
        assertThat(ticketService.findTicketForCustomer(ticketId, "stranger@example.com")).isEmpty();
        assertThat(ticketService.findTicketForCustomer("TKT-999", "aarav@example.com")).isEmpty();
    }

    @Test
    void notesComeBackInChronologicalOrder() {
        String ticketId = createTicket();
        ticketService.updateTicket(ticketId, update(TicketStatus.IN_PROGRESS, "first", currentVersion(ticketId)), AGENT);
        ticketService.updateTicket(ticketId, update(TicketStatus.IN_PROGRESS, "second", currentVersion(ticketId)), AGENT);

        assertThat(ticketService.getTicketByTicketId(ticketId).getNotes())
                .extracting(TicketDetailResponse.NoteDto::getNote_text)
                .containsExactly("first", "second");
    }

    @Test
    void searchFiltersByStatusAndText() {
        String ticketId = createTicket();
        ticketService.createTicket(request("other@example.com", "Billing", "Invoice is wrong"));

        assertThat(ticketService.getAllTickets(null, null)).hasSize(2);
        assertThat(ticketService.getAllTickets(TicketStatus.CLOSED, null)).isEmpty();
        assertThat(ticketService.getAllTickets(null, "invoice")).hasSize(1);
        assertThat(ticketService.getAllTickets(TicketStatus.OPEN, ticketId)).hasSize(1);
    }

    @Test
    void analyticsCountsResolvedAndClosedTogether() {
        String one = createTicket();
        String two = ticketService.createTicket(request("two@example.com", "Two", "Two")).getTicket_id();
        ticketService.createTicket(request("three@example.com", "Three", "Three"));

        ticketService.updateTicket(one, update(TicketStatus.CLOSED, null, currentVersion(one)), AGENT);
        ticketService.updateTicket(two, update(TicketStatus.RESOLVED, null, currentVersion(two)), AGENT);

        var analytics = adminDashboardService.getAdminAnalytics();
        assertThat(analytics.getTotalTickets()).isEqualTo(3);
        assertThat(analytics.getOpenCount()).isEqualTo(1);
        assertThat(analytics.getUnclaimedCount()).isEqualTo(1);
        assertThat(analytics.getInProgressCount()).isZero();
        assertThat(analytics.getResolvedCount()).isEqualTo(2);
        assertThat(analytics.percentOfTotal(analytics.getResolvedCount())).isEqualTo(67);
    }

    @Test
    void workloadCountsActiveAndResolvedSeparatelyBusiestFirst() {
        userRepository.findByUsername("agenttwo").ifPresent(userRepository::delete);
        userRepository.save(User.builder().username("agenttwo").password("x").fullname("Agent Two").role("ROLE_AGENT").build());
        String first = createTicket();
        String second = ticketService.createTicket(request("two@example.com", "Two", "Two")).getTicket_id();
        String third = ticketService.createTicket(request("three@example.com", "Three", "Three")).getTicket_id();
        ticketService.claimTicket(first, AGENT);
        ticketService.resolveTicket(first, AGENT);
        ticketService.claimTicket(second, "agenttwo");
        ticketService.claimTicket(third, "agenttwo");

        var agents = adminDashboardService.getAdminAnalytics().getAgents();
        assertThat(agents).extracting("name").containsExactly("Agent Two", "Agent One");
        assertThat(agents.get(0).getActive()).isEqualTo(2);
        assertThat(agents.get(1).getActive()).isZero();
        assertThat(agents.get(1).getResolved()).isEqualTo(1);
    }

    @Test
    void trendCoversFourteenDaysAndCountsToday() {
        String ticket = createTicket();
        ticketService.createTicket(request("two@example.com", "Two", "Two"));
        ticketService.resolveTicket(ticket, AGENT);

        var analytics = adminDashboardService.getAdminAnalytics();
        assertThat(analytics.getTrend()).hasSize(14);
        var today = analytics.getTrend().getLast();
        assertThat(today.getDate()).isEqualTo(LocalDate.now());
        assertThat(today.getCreated()).isEqualTo(2);
        assertThat(today.getResolved()).isEqualTo(1);
        assertThat(analytics.getNewInTrend()).isEqualTo(2);
        assertThat(analytics.getTrendPeak()).isEqualTo(2);
    }

    @Test
    void averageResolutionTimeAppearsOnceSomethingIsResolved() {
        String ticket = createTicket();
        assertThat(adminDashboardService.getAdminAnalytics().getAverageResolutionTime()).isEqualTo("No data yet");

        ticketService.resolveTicket(ticket, AGENT);
        assertThat(adminDashboardService.getAdminAnalytics().getAverageResolutionTime()).isEqualTo("Under 1m");
    }

    @Test
    void waitingListShowsOnlyUnclaimedOpenTicketsOldestFirst() {
        String older = createTicket();
        String newer = ticketService.createTicket(request("two@example.com", "Two", "Two")).getTicket_id();
        String claimed = ticketService.createTicket(request("three@example.com", "Three", "Three")).getTicket_id();
        ticketService.claimTicket(claimed, AGENT);

        assertThat(adminDashboardService.getAdminAnalytics().getWaitingTickets())
                .extracting("ticketId").containsExactly(older, newer);
    }

    @Test
    void dashboardWorksWithNoTicketsAtAll() {
        var analytics = adminDashboardService.getAdminAnalytics();
        assertThat(analytics.percentOfTotal(0)).isZero();
        assertThat(analytics.getBusiestAgentCount()).isEqualTo(1);
        assertThat(analytics.getTrendPeak()).isEqualTo(1);
        assertThat(analytics.getWaitingTickets()).isEmpty();
    }

    @Test
    void rolledBackUpdateSendsNoEmail() {
        String ticketId = createTicket();
        verify(mailSender, timeout(3000)).send(any(EmailMessage.class));
        reset(mailSender);

        assertThatThrownBy(() -> ticketService.updateTicket(ticketId, update(TicketStatus.CLOSED, null, currentVersion(ticketId)), "ghost"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(mailSender, after(300).never()).send(any(EmailMessage.class));
    }
}
