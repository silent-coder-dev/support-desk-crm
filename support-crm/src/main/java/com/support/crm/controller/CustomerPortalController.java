package com.support.crm.controller;

import com.support.crm.dto.CreateTicketRequest;
import com.support.crm.dto.CreateTicketResponse;
import com.support.crm.service.RequestLimiter;
import com.support.crm.service.TicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/portal")
@RequiredArgsConstructor
public class CustomerPortalController {

    private static final int TOO_MANY_REQUESTS = 429;

    private final TicketService ticketService;
    private final RequestLimiter requestLimiter;

    // One at a time, so a double submit finds the first ticket instead of creating a second one
    private final Object intakeLock = new Object();

    // 1. Complaint Registration Form
    @GetMapping("/inquiry")
    public String openInquiryForm(Model model) {
        if (!model.containsAttribute("ticketRequest")) {
            model.addAttribute("ticketRequest", new CreateTicketRequest());
        }
        return "portal/inquiry";
    }

    // 2. Submit Complaint (the confirmation email goes out from TicketEventListener)
    @PostMapping("/inquiry")
    public String submitInquiry(@Valid @ModelAttribute("ticketRequest") CreateTicketRequest request,
                                BindingResult result,
                                HttpServletRequest httpRequest,
                                HttpServletResponse httpResponse,
                                RedirectAttributes redirectAttributes) {
        // A bot filled in the hidden field: act normal, but save nothing
        if (request.getWebsite() != null && !request.getWebsite().isBlank()) {
            return "redirect:/portal/inquiry";
        }
        if (result.hasErrors()) {
            return "portal/inquiry";
        }
        if (!requestLimiter.allowNewRequest(httpRequest.getRemoteAddr(), request.getCustomer_email(), request.getSubject(), request.getDescription())) {
            result.reject("tooMany", "Too many requests. Please wait a little and try again, or track your existing request.");
            httpResponse.setStatus(TOO_MANY_REQUESTS);
            return "portal/inquiry";
        }

        CreateTicketResponse response;
        synchronized (intakeLock) {
            response = ticketService.createTicket(request);
        }

        redirectAttributes.addFlashAttribute("createdRef", response.getTicket_id());
        redirectAttributes.addFlashAttribute("alreadyOpen", response.isExisting());
        return "redirect:/portal/inquiry?success=true";
    }

    // 3. Track Form (email links open this with the reference already filled in)
    @GetMapping("/track")
    public String openTrackForm(@RequestParam(value = "ref", required = false) String ref, Model model) {
        if (ref != null) {
            model.addAttribute("searchedRef", ref.trim().toUpperCase());
        }
        return "portal/track";
    }

    // 4. Track Complaint Status. POST keeps the email out of URLs and server logs.
    // The same message appears for a wrong reference and a wrong email, so a stranger learns nothing.
    @PostMapping("/track")
    public String trackTicket(@RequestParam("ref") String ref,
                              @RequestParam("email") String email,
                              HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse,
                              Model model) {
        String cleanedRef = ref.trim().toUpperCase();
        model.addAttribute("searchedRef", cleanedRef);
        model.addAttribute("searchedEmail", email.trim());

        // Slows down anyone guessing references and emails
        if (!requestLimiter.allowTrackAttempt(httpRequest.getRemoteAddr())) {
            model.addAttribute("tooMany", true);
            httpResponse.setStatus(TOO_MANY_REQUESTS);
            return "portal/track";
        }

        ticketService.findTicketForCustomer(cleanedRef, email.trim()).ifPresentOrElse(
                ticket -> model.addAttribute("ticket", ticket),
                () -> model.addAttribute("notFound", true)
        );
        return "portal/track";
    }
}
