package com.support.crm.service;

import com.support.crm.dto.*;
import com.support.crm.exception.TicketNotFoundException;
import com.support.crm.model.*;
import com.support.crm.repository.NoteRepository;
import com.support.crm.repository.TicketAuditLogRepository;
import com.support.crm.repository.TicketRepository;
import com.support.crm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketService {

    // Newest tickets shown on one list page; search still reaches older ones
    public static final int TICKET_LIST_LIMIT = 500;

    private final TicketRepository ticketRepository;
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final TicketAuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 1. Create Ticket with Deduplication & Follow-up detection
    @Transactional
    public CreateTicketResponse createTicket(CreateTicketRequest request) {
        String email = request.getCustomer_email().trim().toLowerCase();
        String subject = normalizeSpaces(request.getSubject());
        String description = request.getDescription().trim();
        String comparableDescription = normalizeSpaces(description);

        List<Ticket> activeTickets = ticketRepository.findActiveTicketsByCustomerEmail(email);

        for (Ticket activeTicket : activeTickets) {
            boolean isSameSubject = normalizeSpaces(activeTicket.getSubject()).equalsIgnoreCase(subject);

            if (isSameSubject) {
                boolean isSameDescription = normalizeSpaces(activeTicket.getDescription()).equalsIgnoreCase(comparableDescription);

                if (isSameDescription) {
                    return new CreateTicketResponse(activeTicket.getTicketId(), activeTicket.getCreatedAt(), true);
                } else {
                    Note followUpNote = Note.builder()
                            .ticket(activeTicket)
                            .noteText("[Customer Follow-up / Additional Details]:\n" + description)
                            .authorName("Customer Follow-up")
                            .build();
                    noteRepository.save(followUpNote);
                    activeTicket.getNotes().add(followUpNote);
                    activeTicket.setUpdatedAt(LocalDateTime.now());
                    ticketRepository.save(activeTicket);

                    publishEvent(TicketEvent.Type.UPDATED, activeTicket);
                    return new CreateTicketResponse(activeTicket.getTicketId(), activeTicket.getCreatedAt(), true);
                }
            }
        }

        Ticket savedTicket = ticketRepository.save(Ticket.builder()
                .ticketId("PENDING-" + UUID.randomUUID())
                .customerName(request.getCustomer_name().trim())
                .customerEmail(email)
                .subject(subject)
                .description(description)
                .status(TicketStatus.OPEN)
                .build());

        // Database ka auto-increment ID unique hota hai, isliye do requests ek saath aane par bhi number clash nahi hoga
        savedTicket.setTicketId(String.format("TKT-%03d", savedTicket.getId()));

        publishEvent(TicketEvent.Type.CREATED, savedTicket);
        return new CreateTicketResponse(savedTicket.getTicketId(), savedTicket.getCreatedAt(), false);
    }

    // 2. List newest tickets with SLA stopwatch & Agent mapping
    @Transactional(readOnly = true)
    public List<TicketSummaryResponse> getAllTickets(TicketStatus status, String search) {
        String querySearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        List<Ticket> tickets = ticketRepository.searchTickets(status, querySearch, PageRequest.of(0, TICKET_LIST_LIMIT));

        return tickets.stream()
                .map(t -> new TicketSummaryResponse(
                        t.getTicketId(),
                        t.getCustomerName(),
                        t.getSubject(),
                        t.getStatus(),
                        t.getCreatedAt(),
                        t.getClaimedAt(),
                        t.getAssignedAgentName()))
                .collect(Collectors.toList());
    }

    // 3. Get detailed ticket with chronological notes + Author Info
    @Transactional(readOnly = true)
    public TicketDetailResponse getTicketByTicketId(String ticketId) {
        return toDetailResponse(findTicket(ticketId));
    }

    // 3b. Public tracking: the reference is guessable, so the customer must also know the ticket's email
    @Transactional(readOnly = true)
    public Optional<TicketDetailResponse> findTicketForCustomer(String ticketId, String email) {
        return ticketRepository.findByTicketIdAndCustomerEmailIgnoreCase(ticketId, email)
                .map(this::toDetailResponse);
    }

    // 4. Update ticket status & notes with Real Agent Identity + Note Author
    @Transactional
    public Map<String, Object> updateTicket(String ticketId, UpdateTicketRequest request, String loggedInUsername) {
        Ticket ticket = findTicket(ticketId);

        if (!ticket.getVersion().equals(request.getVersion())) {
            throw new OptimisticLockingFailureException(
                    "Ticket has been modified by another user. Please refresh and review changes."
            );
        }

        User agent = findAgent(loggedInUsername);
        requireHandler(ticket, agent);

        // Auto-claim if ticket is unassigned
        if (ticket.getAssignedAgentId() == null) {
            ticket.setAssignedAgentId(agent.getId());
            ticket.setAssignedAgentName(agent.getFullname());
            if (ticket.getClaimedAt() == null) {
                ticket.setClaimedAt(LocalDateTime.now());
            }
        }

        boolean statusChanged = request.getStatus() != null && request.getStatus() != ticket.getStatus();
        boolean noteAdded = request.getNotes() != null && !request.getNotes().trim().isEmpty();
        boolean justResolved = request.getStatus() != null && changeStatus(ticket, request.getStatus());

        if (noteAdded) {
            Note note = Note.builder()
                    .ticket(ticket)
                    .noteText(request.getNotes().trim())
                    .authorName(agent.getFullname())
                    .build();
            noteRepository.save(note);
            ticket.getNotes().add(note);
        }

        ticket.setUpdatedAt(LocalDateTime.now());
        // Flush now so the response carries the new version number, not the old one
        Ticket updatedTicket = ticketRepository.saveAndFlush(ticket);

        logAudit(ticketId, agent, justResolved ? "RESOLVED_TICKET" : "UPDATED_STATUS");
        publishEvent(justResolved ? TicketEvent.Type.RESOLVED
                : (statusChanged || noteAdded) ? TicketEvent.Type.CUSTOMER_UPDATE : TicketEvent.Type.UPDATED, updatedTicket);

        return Map.of(
                "success", true,
                "version", updatedTicket.getVersion(),
                "updated_at", updatedTicket.getUpdatedAt()
        );
    }

    // 5. Claim ticket by assigned agent
    @Transactional
    public void claimTicket(String ticketId, String loggedInUsername) {
        Ticket ticket = findTicket(ticketId);

        if (ticket.getAssignedAgentId() != null) {
            throw new IllegalStateException("Ticket already claimed by " + ticket.getAssignedAgentName());
        }
        if (isFinished(ticket.getStatus())) {
            throw new IllegalStateException("Ticket is already closed.");
        }

        User agent = findAgent(loggedInUsername);

        ticket.setAssignedAgentId(agent.getId());
        ticket.setAssignedAgentName(agent.getFullname());
        ticket.setStatus(TicketStatus.IN_PROGRESS);
        ticket.setClaimedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        logAudit(ticketId, agent, "CLAIMED_TICKET");
        publishEvent(TicketEvent.Type.CUSTOMER_UPDATE, ticket);
    }

    // 6. Resolve ticket by assigned agent
    @Transactional
    public void resolveTicket(String ticketId, String loggedInUsername) {
        Ticket ticket = findTicket(ticketId);
        User agent = findAgent(loggedInUsername);
        requireHandler(ticket, agent);

        boolean justResolved = changeStatus(ticket, TicketStatus.CLOSED);
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        logAudit(ticketId, agent, "RESOLVED_TICKET");
        // Closing an already closed ticket must not email the customer a second time
        publishEvent(justResolved ? TicketEvent.Type.RESOLVED : TicketEvent.Type.UPDATED, ticket);
    }

    private Ticket findTicket(String ticketId) {
        return ticketRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
    }

    private User findAgent(String username) {
        return userRepository.findByUsername(username.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + username));
    }

    // Once someone is working on a ticket, only they or an admin can change it.
    // Two people saving at the same moment are still caught by @Version.
    private void requireHandler(Ticket ticket, User agent) {
        boolean heldBySomeoneElse = ticket.getAssignedAgentId() != null && !ticket.getAssignedAgentId().equals(agent.getId());
        if (heldBySomeoneElse && !agent.getRole().endsWith("ADMIN")) {
            throw new IllegalStateException("This ticket is being handled by " + ticket.getAssignedAgentName() + ". Only they or an admin can update it.");
        }
    }

    private String normalizeSpaces(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private boolean isFinished(TicketStatus status) {
        return status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED;
    }

    // Returns true only at the moment an active ticket becomes resolved
    private boolean changeStatus(Ticket ticket, TicketStatus newStatus) {
        boolean wasFinished = isFinished(ticket.getStatus());
        boolean nowFinished = isFinished(newStatus);

        ticket.setStatus(newStatus);
        if (nowFinished && !wasFinished) {
            ticket.setResolvedAt(LocalDateTime.now());
        } else if (!nowFinished) {
            ticket.setResolvedAt(null);
        }
        return nowFinished && !wasFinished;
    }

    private void logAudit(String ticketId, User agent, String action) {
        auditLogRepository.save(TicketAuditLog.builder()
                .ticketId(ticketId)
                .userId(agent.getId())
                .userName(agent.getFullname())
                .action(action)
                .timestamp(LocalDateTime.now())
                .build());
    }

    private void publishEvent(TicketEvent.Type type, Ticket ticket) {
        eventPublisher.publishEvent(
                new TicketEvent(type, ticket.getTicketId(), ticket.getCustomerEmail(), ticket.getSubject(), ticket.getStatus()));
    }

    private TicketDetailResponse toDetailResponse(Ticket ticket) {
        List<TicketDetailResponse.NoteDto> noteDtos = ticket.getNotes().stream()
                .map(n -> new TicketDetailResponse.NoteDto(
                        n.getNoteText(),
                        n.getCreatedAt(),
                        n.getAuthorName() != null ? n.getAuthorName() : "Support Agent"
                ))
                .collect(Collectors.toList());

        return new TicketDetailResponse(
                ticket.getTicketId(),
                ticket.getCustomerName(),
                ticket.getCustomerEmail(),
                ticket.getSubject(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getVersion(),
                ticket.getAssignedAgentId(),
                ticket.getAssignedAgentName(),
                noteDtos
        );
    }
}
