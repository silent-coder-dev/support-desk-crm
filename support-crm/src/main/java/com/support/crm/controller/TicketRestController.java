package com.support.crm.controller;

import com.support.crm.dto.*;
import com.support.crm.model.TicketStatus;
import com.support.crm.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Errors are handled in ApiExceptionHandler
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketRestController {

    private final TicketService ticketService;

    // 1. POST /api/tickets
    @PostMapping
    public ResponseEntity<CreateTicketResponse> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        CreateTicketResponse response = ticketService.createTicket(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // 2. GET /api/tickets?status=OPEN&search=query
    @GetMapping
    public ResponseEntity<List<TicketSummaryResponse>> getTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) String search) {
        List<TicketSummaryResponse> tickets = ticketService.getAllTickets(status, search);
        return ResponseEntity.ok(tickets);
    }

    // 3. GET /api/tickets/{ticket_id}
    @GetMapping("/{ticket_id}")
    public ResponseEntity<TicketDetailResponse> getTicket(@PathVariable("ticket_id") String ticketId) {
        TicketDetailResponse ticket = ticketService.getTicketByTicketId(ticketId);
        return ResponseEntity.ok(ticket);
    }

    // 4. PUT /api/tickets/{ticket_id} with Optimistic Lock Conflict Handling
    @PutMapping("/{ticket_id}")
    public ResponseEntity<Map<String, Object>> updateTicket(
            @PathVariable("ticket_id") String ticketId,
            @Valid @RequestBody UpdateTicketRequest request,
            Authentication auth) {
        return ResponseEntity.ok(ticketService.updateTicket(ticketId, request, auth.getName()));
    }

    // 5. POST /api/tickets/{ticket_id}/claim
    @PostMapping("/{ticket_id}/claim")
    public ResponseEntity<Map<String, Object>> claimTicket(@PathVariable("ticket_id") String ticketId, Authentication auth) {
        ticketService.claimTicket(ticketId, auth.getName());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Ticket " + ticketId + " claimed by " + auth.getName()
        ));
    }

    // 6. POST /api/tickets/{ticket_id}/resolve
    @PostMapping("/{ticket_id}/resolve")
    public ResponseEntity<Map<String, Object>> resolveTicket(@PathVariable("ticket_id") String ticketId, Authentication auth) {
        ticketService.resolveTicket(ticketId, auth.getName());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Ticket " + ticketId + " marked as resolved by " + auth.getName()
        ));
    }

    // 7. GET /api/tickets/csrf: POST, PUT calls must send this token in a header
    @GetMapping("/csrf")
    public Map<String, String> csrfToken(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
}
