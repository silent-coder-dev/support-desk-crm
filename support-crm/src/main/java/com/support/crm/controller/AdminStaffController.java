package com.support.crm.controller;

import com.support.crm.dto.CreateStaffRequest;
import com.support.crm.model.User;
import com.support.crm.repository.UserRepository;
import com.support.crm.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Everything under /admin needs the ADMIN role (see SecurityConfig)
@Controller
@RequestMapping("/admin/staff")
@RequiredArgsConstructor
public class AdminStaffController {

    private final StaffService staffService;
    private final UserRepository userRepository;

    // 1. Staff list
    @GetMapping
    public String listStaff(Model model) {
        model.addAttribute("staff", staffService.getAllStaff());
        model.addAttribute("activeTickets", staffService.getActiveTicketCounts());
        return "tickets/admin/staff";
    }

    // 2. Add staff form
    @GetMapping("/new")
    public String newStaffForm(Model model) {
        if (!model.containsAttribute("staffRequest")) {
            model.addAttribute("staffRequest", new CreateStaffRequest());
        }
        return "tickets/admin/staff-new";
    }

    // 3. Create the account and show its temporary password once
    @PostMapping
    public String createStaff(@Valid @ModelAttribute("staffRequest") CreateStaffRequest request,
                              BindingResult result,
                              RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "tickets/admin/staff-new";
        }

        // Unique checks across Username, Email, and Mobile
        if (userRepository.existsByUsername(request.getUsername().trim().toLowerCase())) {
            result.rejectValue("username", "duplicate", "This username is already taken.");
        }
        if (userRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            result.rejectValue("email", "duplicate", "This email address is already registered.");
        }
        if (!request.getMobileNumber().isBlank() && userRepository.existsByMobileNumber(request.getMobileNumber().trim())) {
            result.rejectValue("mobileNumber", "duplicate", "This mobile number is already linked to another account.");
        }
        if (result.hasErrors()) {
            return "tickets/admin/staff-new";
        }

        String temporaryPassword = staffService.createStaff(request);
        showTemporaryPassword(redirectAttributes, request.getFullname().trim(), request.getUsername().trim().toLowerCase(), temporaryPassword);
        return "redirect:/admin/staff";
    }

    // 4. Block a login
    @PostMapping("/{id}/disable")
    public String disableStaff(@PathVariable("id") Long id, Authentication auth, RedirectAttributes redirectAttributes) {
        return changeAccess(id, true, auth, redirectAttributes);
    }

    // 5. Restore a login
    @PostMapping("/{id}/enable")
    public String enableStaff(@PathVariable("id") Long id, Authentication auth, RedirectAttributes redirectAttributes) {
        return changeAccess(id, false, auth, redirectAttributes);
    }

    // 6. New temporary password
    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable("id") Long id, Authentication auth, RedirectAttributes redirectAttributes) {
        try {
            String temporaryPassword = staffService.resetPassword(id, auth.getName());
            User member = staffService.findStaff(id);
            showTemporaryPassword(redirectAttributes, member.getFullname(), member.getUsername(), temporaryPassword);
        } catch (IllegalArgumentException ex) {
            addToast(redirectAttributes, "danger", ex.getMessage());
        }
        return "redirect:/admin/staff";
    }

    private String changeAccess(Long id, boolean disabled, Authentication auth, RedirectAttributes redirectAttributes) {
        try {
            User member = staffService.setDisabled(id, disabled, auth.getName());
            addToast(redirectAttributes, "success", member.getFullname() + (disabled ? " can no longer sign in." : " can sign in again."));
        } catch (IllegalArgumentException ex) {
            addToast(redirectAttributes, "danger", ex.getMessage());
        }
        return "redirect:/admin/staff";
    }

    // Flash attributes last for one page view, so the password is never stored or shown again
    private void showTemporaryPassword(RedirectAttributes redirectAttributes, String name, String username, String password) {
        redirectAttributes.addFlashAttribute("newStaffName", name);
        redirectAttributes.addFlashAttribute("newStaffUsername", username);
        redirectAttributes.addFlashAttribute("newStaffPassword", password);
    }

    private void addToast(RedirectAttributes redirectAttributes, String type, String message) {
        redirectAttributes.addFlashAttribute("toastMessage", message);
        redirectAttributes.addFlashAttribute("toastType", type);
    }
}
