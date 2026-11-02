package com.support.crm.controller;

import com.support.crm.dto.ChangePasswordRequest;
import com.support.crm.dto.UpdateProfileRequest;
import com.support.crm.model.TicketStatus;
import com.support.crm.model.User;
import com.support.crm.repository.TicketAuditLogRepository;
import com.support.crm.repository.TicketRepository;
import com.support.crm.repository.UserRepository;
import com.support.crm.service.CloudinaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final TicketAuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final CloudinaryService cloudinaryService;

    @GetMapping
    public String viewProfile(Authentication auth, Model model) {
        User user = findUser(auth);

        model.addAttribute("user", user);
        model.addAttribute("activeTickets", ticketRepository.countByAssignedAgentIdAndStatusIn(
                user.getId(), List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS)));
        model.addAttribute("resolvedTickets", ticketRepository.countByAssignedAgentIdAndStatusIn(
                user.getId(), List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED)));
        model.addAttribute("recentActions", auditLogRepository.findTop5ByUserIdOrderByTimestampDesc(user.getId()));

        if (!model.containsAttribute("detailsRequest")) {
            UpdateProfileRequest details = new UpdateProfileRequest();
            details.setFullname(user.getFullname());
            details.setEmail(user.getEmail());
            details.setMobileNumber(user.getMobileNumber());
            model.addAttribute("detailsRequest", details);
        }
        if (!model.containsAttribute("passwordRequest")) {
            model.addAttribute("passwordRequest", new ChangePasswordRequest());
        }

        return "profile/view";
    }

    @PostMapping("/details")
    public String updateDetails(@Valid @ModelAttribute("detailsRequest") UpdateProfileRequest request,
                                BindingResult bindingResult,
                                Authentication auth,
                                RedirectAttributes redirectAttributes) {
        User user = findUser(auth);

        if (!bindingResult.hasErrors()) {
            String email = request.getEmail().trim().toLowerCase();
            String mobile = request.getMobileNumber() == null ? "" : request.getMobileNumber().trim();

            if (userRepository.existsByEmailAndIdNot(email, user.getId())) {
                bindingResult.rejectValue("email", "duplicate", "This email address is already registered.");
            }
            if (!mobile.isEmpty() && userRepository.existsByMobileNumberAndIdNot(mobile, user.getId())) {
                bindingResult.rejectValue("mobileNumber", "duplicate", "This mobile number is already linked to another account.");
            }

            if (!bindingResult.hasErrors()) {
                user.setFullname(request.getFullname().trim());
                user.setEmail(email);
                user.setMobileNumber(mobile.isEmpty() ? null : mobile);
                userRepository.save(user);

                redirectAttributes.addFlashAttribute("detailsSuccess", "Your details were updated.");
                return "redirect:/profile";
            }
        }

        redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.detailsRequest", bindingResult);
        redirectAttributes.addFlashAttribute("detailsRequest", request);
        redirectAttributes.addFlashAttribute("detailsError", "Details not saved. Please check the form.");
        return "redirect:/profile";
    }

    @PostMapping("/avatar")
    public String uploadAvatar(@RequestParam("avatarFile") MultipartFile file,
                               Authentication auth,
                               RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("avatarError", "Please select a photo to upload.");
            return "redirect:/profile";
        }

        try {
            User user = findUser(auth);

            // Upload directly to Cloudinary and get the persistent HTTPS CDN URL
            String cdnUrl = cloudinaryService.uploadAvatar(file, user.getId());

            // Save the CDN URL to the user record
            user.setAvatarUrl(cdnUrl);
            userRepository.save(user);

            redirectAttributes.addFlashAttribute("avatarSuccess", "Profile avatar updated successfully!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("avatarError", e.getMessage());
        } catch (Exception e) {
            log.error("Cloudinary upload failed: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("avatarError", "Failed to upload photo. Please try again.");
        }

        return "redirect:/profile";
    }

    @PostMapping("/change-password")
    public String changePassword(@Valid @ModelAttribute("passwordRequest") ChangePasswordRequest request,
                                 BindingResult bindingResult,
                                 Authentication auth,
                                 RedirectAttributes redirectAttributes) {
        User user = findUser(auth);

        // Field checks run first, so the comparisons below never see a missing value
        if (!bindingResult.hasErrors()) {
            if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                bindingResult.rejectValue("currentPassword", "mismatch", "Incorrect current password.");
            }

            if (!request.getNewPassword().equals(request.getConfirmPassword())) {
                bindingResult.rejectValue("confirmPassword", "nomatch", "New password and confirmation do not match.");
            }
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.passwordRequest", bindingResult);
            redirectAttributes.addFlashAttribute("passwordRequest", request);
            redirectAttributes.addFlashAttribute("passwordError", "Password not changed. Please check the form.");
            return "redirect:/profile";
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("passwordSuccess", "Password updated successfully!");
        return "redirect:/profile";
    }

    private User findUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName().trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + auth.getName()));
    }
}
