package com.support.crm.controller;

import com.support.crm.dto.CreateTicketRequest;
import com.support.crm.dto.CreateTicketResponse;
import com.support.crm.dto.TicketDetailResponse;
import com.support.crm.dto.TicketSummaryResponse;
import com.support.crm.dto.UpdateTicketRequest;
import com.support.crm.model.TicketStatus;
import com.support.crm.service.SseNotificationService;
import com.support.crm.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class TicketWebController {

    private final TicketService ticketService;
    private final SseNotificationService sseNotificationService;

    @GetMapping("/")
    public String rootRedirect() {
        return "landing";
    }

    // 1. Agent Queue Dashboard
    @GetMapping("/tickets")
    public String dashboard(@RequestParam(required = false) TicketStatus status,
                            @RequestParam(required = false) String search,
                            Model model) {
        List<TicketSummaryResponse> tickets = ticketService.getAllTickets(status, search);
        model.addAttribute("tickets", tickets);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("searchQuery", search);
        return "tickets/dashboard";
    }

    // 2. Direct Handler for UI requests hitting /tickets/admin/all-tickets
    @GetMapping("/tickets/admin/all-tickets")
    public String adminAllTicketsView(@RequestParam(required = false) TicketStatus status,
                                      @RequestParam(required = false) String search,
                                      Model model) {
        List<TicketSummaryResponse> tickets = ticketService.getAllTickets(status, search);
        model.addAttribute("tickets", tickets);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("searchQuery", search);
        return "tickets/admin/all-tickets";
    }

    // 3. Fallbacks for admin navigation links
    @GetMapping("/tickets/admin/dashboard")
    public String returnToAdminDashboard() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/tickets/admin/{ticket_id}")
    public String adminInspectFallback(@PathVariable("ticket_id") String ticketId) {
        return "redirect:/admin/tickets/" + ticketId;
    }

    @GetMapping("/tickets/new")
    public String createForm(Model model) {
        if (!model.containsAttribute("ticketRequest")) {
            model.addAttribute("ticketRequest", new CreateTicketRequest());
        }
        return "tickets/create";
    }

    @PostMapping("/tickets/new")
    public String submitCreate(@Valid @ModelAttribute("ticketRequest") CreateTicketRequest request,
                               BindingResult bindingResult,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "tickets/create";
        }

        CreateTicketResponse response = ticketService.createTicket(request);
        redirectAttributes.addFlashAttribute("toastMessage", "Inquiry processed successfully under Ticket " + response.getTicket_id());
        redirectAttributes.addFlashAttribute("toastType", "success");
        return "redirect:/tickets";
    }

    @GetMapping("/tickets/{ticket_id}")
    public String viewDetail(@PathVariable String ticket_id, Model model) {
        TicketDetailResponse ticket = ticketService.getTicketByTicketId(ticket_id);
        model.addAttribute("ticket", ticket);
        if (!model.containsAttribute("updateRequest")) {
            UpdateTicketRequest updateReq = new UpdateTicketRequest();
            updateReq.setStatus(ticket.getStatus());
            updateReq.setVersion(ticket.getVersion());
            model.addAttribute("updateRequest", updateReq);
        }
        return "tickets/detail";
    }

    @PostMapping("/tickets/{ticket_id}/update")
    public String updateTicket(@PathVariable String ticket_id,
                               @ModelAttribute("updateRequest") UpdateTicketRequest request,
                               Authentication auth,
                               RedirectAttributes redirectAttributes) {
        try {
            String loggedInUsername = auth != null ? auth.getName() : null;
            ticketService.updateTicket(ticket_id, request, loggedInUsername);
            redirectAttributes.addFlashAttribute("toastMessage", "Ticket status and notes updated successfully!");
            redirectAttributes.addFlashAttribute("toastType", "success");
        } catch (OptimisticLockingFailureException ex) {
            redirectAttributes.addFlashAttribute("toastMessage", "Conflict detected: Ticket was updated by another operator. Please review latest state.");
            redirectAttributes.addFlashAttribute("toastType", "danger");
        }

        return "redirect:/tickets/" + ticket_id;
    }

    @PostMapping("/tickets/{ticket_id}/claim")
    public String claimTicket(@PathVariable("ticket_id") String ticketId,
                              Authentication auth,
                              RedirectAttributes redirectAttributes) {
        try {
            ticketService.claimTicket(ticketId, auth != null ? auth.getName() : null);
            redirectAttributes.addFlashAttribute("toastMessage", "Ticket successfully claimed by you!");
            redirectAttributes.addFlashAttribute("toastType", "success");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("toastMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("toastType", "danger");
        }
        return "redirect:/tickets/" + ticketId;
    }

    @PostMapping("/tickets/{ticket_id}/resolve")
    public String resolveTicket(@PathVariable("ticket_id") String ticketId,
                                Authentication auth,
                                RedirectAttributes redirectAttributes) {
        try {
            ticketService.resolveTicket(ticketId, auth != null ? auth.getName() : null);
            redirectAttributes.addFlashAttribute("toastMessage", "Ticket marked as CLOSED!");
            redirectAttributes.addFlashAttribute("toastType", "success");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("toastMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("toastType", "danger");
        }
        return "redirect:/tickets/" + ticketId;
    }

    @GetMapping("/tickets/live-stream")
    @ResponseBody
    public SseEmitter liveStream() {
        return sseNotificationService.subscribe();
    }
}