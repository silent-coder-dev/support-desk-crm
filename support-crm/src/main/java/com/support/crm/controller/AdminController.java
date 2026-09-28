package com.support.crm.controller;

import com.support.crm.dto.AdminAnalyticsResponse;
import com.support.crm.dto.TicketDetailResponse;
import com.support.crm.dto.TicketSummaryResponse;
import com.support.crm.model.TicketAuditLog;
import com.support.crm.model.TicketStatus;
import com.support.crm.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final TicketService ticketService;

    // 1. Executive Operations & Analytics Dashboard
    @GetMapping("/dashboard")
    public String adminDashboard(Model model) {
        AdminAnalyticsResponse analytics = ticketService.getAdminAnalytics();
        List<TicketAuditLog> recentLogs = ticketService.getRecentAuditLogs();

        model.addAttribute("analytics", analytics);
        model.addAttribute("auditLogs", recentLogs);

        return "tickets/admin/dashboard";
    }

    // 2. Admin Inspection View: All Tickets Registry (/admin/tickets and /admin/all-tickets)
    @GetMapping({"/tickets", "/all-tickets"})
    public String viewAllTickets(@RequestParam(required = false) TicketStatus status,
                                 @RequestParam(required = false) String search,
                                 Model model) {
        List<TicketSummaryResponse> tickets = ticketService.getAllTickets(status, search);
        model.addAttribute("tickets", tickets);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("searchQuery", search);
        return "tickets/admin/all-tickets";
    }

    // 3. Admin Ticket Detail (Read-only Deep Inspection)
    @GetMapping("/tickets/{ticketId}")
    public String inspectTicketDetail(@PathVariable("ticketId") String ticketId, Model model) {
        TicketDetailResponse ticket = ticketService.getTicketByTicketId(ticketId);
        model.addAttribute("ticket", ticket);
        return "tickets/admin/detail";
    }
}