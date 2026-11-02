package com.support.crm.service;

import com.support.crm.dto.AdminAnalyticsResponse;
import com.support.crm.dto.AdminAnalyticsResponse.AgentLoad;
import com.support.crm.dto.AdminAnalyticsResponse.DayCount;
import com.support.crm.dto.AdminAnalyticsResponse.WaitingTicket;
import com.support.crm.model.TicketAuditLog;
import com.support.crm.model.TicketStatus;
import com.support.crm.repository.TicketAuditLogRepository;
import com.support.crm.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Everything shown on the admin dashboard. The database does the counting, so no ticket rows are loaded just to count them.
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final int TREND_DAYS = 14;
    private static final int AVERAGE_RESOLUTION_DAYS = 30;

    private final TicketRepository ticketRepository;
    private final TicketAuditLogRepository auditLogRepository;

    // 1. All dashboard numbers
    @Transactional(readOnly = true)
    public AdminAnalyticsResponse getAdminAnalytics() {
        return AdminAnalyticsResponse.builder()
                .totalTickets(ticketRepository.count())
                .openCount(ticketRepository.countByStatus(TicketStatus.OPEN))
                .unclaimedCount(ticketRepository.countByStatusAndAssignedAgentIdIsNull(TicketStatus.OPEN))
                .inProgressCount(ticketRepository.countByStatus(TicketStatus.IN_PROGRESS))
                .resolvedCount(ticketRepository.countByStatus(TicketStatus.RESOLVED)
                        + ticketRepository.countByStatus(TicketStatus.CLOSED))
                .averageResolutionTime(averageResolutionTime())
                .agents(agentLoads())
                .trend(trend())
                .waitingTickets(waitingTickets())
                .build();
    }

    // 2. Recent Audit Logs for Activity Feed
    @Transactional(readOnly = true)
    public List<TicketAuditLog> getRecentAuditLogs() {
        return auditLogRepository.findTop8ByOrderByTimestampDesc();
    }

    private String averageResolutionTime() {
        LocalDateTime since = LocalDateTime.now().minusDays(AVERAGE_RESOLUTION_DAYS);
        Double seconds = ticketRepository.averageResolutionSecondsSince(since);
        return seconds == null ? "No data yet" : describe(Duration.ofSeconds(seconds.longValue()));
    }

    private List<AgentLoad> agentLoads() {
        return ticketRepository.countTicketsPerAgent().stream()
                .map(row -> new AgentLoad((String) row[0], (Long) row[1], (Long) row[2]))
                .collect(Collectors.toList());
    }

    // One entry per day, including days with no tickets, so the chart has no gaps
    private List<DayCount> trend() {
        LocalDate firstDay = LocalDate.now().minusDays(TREND_DAYS - 1);
        Map<LocalDate, Long> created = toDayMap(ticketRepository.countCreatedPerDaySince(firstDay.atStartOfDay()));
        Map<LocalDate, Long> resolved = toDayMap(ticketRepository.countResolvedPerDaySince(firstDay.atStartOfDay()));

        List<DayCount> days = new ArrayList<>();
        for (int i = 0; i < TREND_DAYS; i++) {
            LocalDate day = firstDay.plusDays(i);
            days.add(new DayCount(day, created.getOrDefault(day, 0L), resolved.getOrDefault(day, 0L)));
        }
        return days;
    }

    private Map<LocalDate, Long> toDayMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (LocalDate) row[0], row -> (Long) row[1]));
    }

    private List<WaitingTicket> waitingTickets() {
        LocalDateTime now = LocalDateTime.now();
        return ticketRepository.findTop5ByStatusAndAssignedAgentIdIsNullOrderByCreatedAtAsc(TicketStatus.OPEN).stream()
                .map(t -> new WaitingTicket(t.getTicketId(), t.getCustomerName(), t.getSubject(),
                        describe(Duration.between(t.getCreatedAt(), now))))
                .collect(Collectors.toList());
    }

    // 90 seconds becomes "1m", 26 hours becomes "1d 2h"
    static String describe(Duration duration) {
        if (duration.toDays() > 0) {
            return duration.toDays() + "d " + duration.toHoursPart() + "h";
        }
        if (duration.toHours() > 0) {
            return duration.toHours() + "h " + duration.toMinutesPart() + "m";
        }
        return duration.toMinutes() > 0 ? duration.toMinutes() + "m" : "Under 1m";
    }
}
