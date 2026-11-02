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

    private static final String CONFLICT_MESSAGE = "Conflict detected: Ticket was updated by another operator. Please review latest state.";

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
        model.addAttribute("ticketLimit", TicketService.TICKET_LIST_LIMIT);
        return "tickets/dashboard";
    }

    // 2. Aliases for admin navigation links
    @GetMapping("/tickets/admin/all-tickets")
    public String adminAllTicketsView() {
        return "forward:/admin/tickets";
    }

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
    public String viewDetail(@PathVariable("ticket_id") String ticketId, Model model) {
        TicketDetailResponse ticket = ticketService.getTicketByTicketId(ticketId);
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
    public String updateTicket(@PathVariable("ticket_id") String ticketId,
                               @Valid @ModelAttribute("updateRequest") UpdateTicketRequest request,
                               BindingResult bindingResult,
                               Authentication auth,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addToast(redirectAttributes, "danger", bindingResult.getAllErrors().getFirst().getDefaultMessage());
            return "redirect:/tickets/" + ticketId;
        }

        try {
            ticketService.updateTicket(ticketId, request, auth.getName());
            addToast(redirectAttributes, "success", "Ticket status and notes updated successfully!");
        } catch (IllegalStateException ex) {
            addToast(redirectAttributes, "danger", ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            addToast(redirectAttributes, "danger", CONFLICT_MESSAGE);
        }

        return "redirect:/tickets/" + ticketId;
    }

    @PostMapping("/tickets/{ticket_id}/claim")
    public String claimTicket(@PathVariable("ticket_id") String ticketId,
                              Authentication auth,
                              RedirectAttributes redirectAttributes) {
        try {
            ticketService.claimTicket(ticketId, auth.getName());
            addToast(redirectAttributes, "success", "Ticket successfully claimed by you!");
        } catch (IllegalStateException ex) {
            addToast(redirectAttributes, "danger", ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            addToast(redirectAttributes, "danger", CONFLICT_MESSAGE);
        }
        return "redirect:/tickets/" + ticketId;
    }

    @PostMapping("/tickets/{ticket_id}/resolve")
    public String resolveTicket(@PathVariable("ticket_id") String ticketId,
                                Authentication auth,
                                RedirectAttributes redirectAttributes) {
        try {
            ticketService.resolveTicket(ticketId, auth.getName());
            addToast(redirectAttributes, "success", "Ticket marked as CLOSED!");
        } catch (IllegalStateException ex) {
            addToast(redirectAttributes, "danger", ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            addToast(redirectAttributes, "danger", CONFLICT_MESSAGE);
        }
        return "redirect:/tickets/" + ticketId;
    }

    @GetMapping("/tickets/live-stream")
    @ResponseBody
    public SseEmitter liveStream() {
        return sseNotificationService.subscribe();
    }

    private void addToast(RedirectAttributes redirectAttributes, String type, String message) {
        redirectAttributes.addFlashAttribute("toastMessage", message);
        redirectAttributes.addFlashAttribute("toastType", type);
    }
}
