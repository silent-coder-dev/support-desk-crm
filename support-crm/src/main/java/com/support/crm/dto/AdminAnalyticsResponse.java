package com.support.crm.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAnalyticsResponse {
    private long totalTickets;
    private long openCount;
    private long unclaimedCount;
    private long inProgressCount;
    private long resolvedCount;
    private String averageResolutionTime;
    private List<AgentLoad> agents; // Busiest agent first
    private List<DayCount> trend; // Oldest day first
    private List<WaitingTicket> waitingTickets; // Longest wait first

    public long percentOfTotal(long count) {
        return totalTickets == 0 ? 0 : Math.round(count * 100.0 / totalTickets);
    }

    // The longest workload bar is measured against this
    public long getBusiestAgentCount() {
        return Math.max(1, agents.stream().mapToLong(AgentLoad::getActive).max().orElse(1));
    }

    // The tallest column on the trend chart is measured against this
    public long getTrendPeak() {
        return Math.max(1, trend.stream().mapToLong(d -> Math.max(d.getCreated(), d.getResolved())).max().orElse(1));
    }

    public long getNewInTrend() {
        return trend.stream().mapToLong(DayCount::getCreated).sum();
    }

    public long getResolvedInTrend() {
        return trend.stream().mapToLong(DayCount::getResolved).sum();
    }

    @Getter
    @AllArgsConstructor
    public static class AgentLoad {
        private String name;
        private long active;
        private long resolved;
    }

    @Getter
    @AllArgsConstructor
    public static class DayCount {
        private LocalDate date;
        private long created;
        private long resolved;
    }

    @Getter
    @AllArgsConstructor
    public static class WaitingTicket {
        private String ticketId;
        private String customerName;
        private String subject;
        private String waitingFor;
    }
}
