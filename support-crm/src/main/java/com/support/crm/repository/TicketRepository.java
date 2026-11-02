package com.support.crm.repository;

import com.support.crm.model.Ticket;
import com.support.crm.model.TicketStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByTicketId(String ticketId);

    Optional<Ticket> findByTicketIdAndCustomerEmailIgnoreCase(String ticketId, String customerEmail);

    long countByStatus(TicketStatus status);

    // Query to find all active tickets (OPEN or IN_PROGRESS) for a specific customer email
    @Query("SELECT t FROM Ticket t WHERE LOWER(t.customerEmail) = LOWER(:email) AND t.status IN ('OPEN', 'IN_PROGRESS') ORDER BY t.createdAt DESC")
    List<Ticket> findActiveTicketsByCustomerEmail(@Param("email") String email);

    @Query("SELECT t FROM Ticket t WHERE " +
            "(:status IS NULL OR t.status = :status) AND " +
            "(:search IS NULL OR :search = '' OR " +
            "LOWER(t.customerName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(t.customerEmail) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(t.ticketId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(t.subject) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "ORDER BY t.createdAt DESC")
    List<Ticket> searchTickets(@Param("status") TicketStatus status, @Param("search") String search, Pageable pageable);

    long countByStatusAndAssignedAgentIdIsNull(TicketStatus status);

    long countByAssignedAgentIdAndStatusIn(Long assignedAgentId, Collection<TicketStatus> statuses);

    // Each row is [agent id, active tickets]
    @Query("SELECT t.assignedAgentId, COUNT(t) FROM Ticket t WHERE t.assignedAgentId IS NOT NULL AND t.status IN ('OPEN', 'IN_PROGRESS') GROUP BY t.assignedAgentId")
    List<Object[]> countActiveTicketsPerAgentId();

    List<Ticket> findTop5ByStatusAndAssignedAgentIdIsNullOrderByCreatedAtAsc(TicketStatus status);

    // Each row is [agent name, active tickets, resolved tickets], busiest agent first
    @Query("SELECT t.assignedAgentName, " +
            "SUM(CASE WHEN t.status IN ('OPEN', 'IN_PROGRESS') THEN 1L ELSE 0L END) AS active, " +
            "SUM(CASE WHEN t.status IN ('RESOLVED', 'CLOSED') THEN 1L ELSE 0L END) " +
            "FROM Ticket t WHERE t.assignedAgentName IS NOT NULL AND t.assignedAgentName <> '' " +
            "GROUP BY t.assignedAgentName ORDER BY active DESC, t.assignedAgentName")
    List<Object[]> countTicketsPerAgent();

    // Each row is [day, tickets created that day]
    @Query("SELECT CAST(t.createdAt AS LocalDate), COUNT(t) FROM Ticket t WHERE t.createdAt >= :since GROUP BY CAST(t.createdAt AS LocalDate)")
    List<Object[]> countCreatedPerDaySince(@Param("since") LocalDateTime since);

    // Each row is [day, tickets resolved that day]
    @Query("SELECT CAST(t.resolvedAt AS LocalDate), COUNT(t) FROM Ticket t WHERE t.resolvedAt >= :since GROUP BY CAST(t.resolvedAt AS LocalDate)")
    List<Object[]> countResolvedPerDaySince(@Param("since") LocalDateTime since);

    // Null when nothing was resolved in the period
    @Query("SELECT AVG((t.resolvedAt - t.createdAt) BY SECOND) FROM Ticket t WHERE t.resolvedAt >= :since")
    Double averageResolutionSecondsSince(@Param("since") LocalDateTime since);
}
