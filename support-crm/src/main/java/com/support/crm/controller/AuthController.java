package com.support.crm.controller;

import com.support.crm.dto.RegisterRequest;
import com.support.crm.model.User;
import com.support.crm.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private static final String SIGNUP_CLOSED_REDIRECT = "redirect:/staff-login";
    private static final String SIGNUP_CLOSED_MESSAGE = "Agent registration is closed. Please contact your administrator.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.signup.enabled:false}")
    private boolean signupEnabled;

    // Old links keep working
    @GetMapping("/login")
    public String oldLoginPage() {
        return "redirect:/staff-login";
    }

    @GetMapping("/admin-login")
    public String adminLoginPage(@RequestParam(value = "error", required = false) String error,
                                 @RequestParam(value = "logout", required = false) String logout,
                                 @RequestParam(value = "expired", required = false) String expired,
                                 Model model) {
        model.addAttribute("portal", "admin");
        return loginPage(error, logout, expired, model);
    }

    @GetMapping("/staff-login")
    public String staffLoginPage(@RequestParam(value = "error", required = false) String error,
                                 @RequestParam(value = "logout", required = false) String logout,
                                 @RequestParam(value = "expired", required = false) String expired,
                                 Model model) {
        model.addAttribute("portal", "staff");
        return loginPage(error, logout, expired, model);
    }

    private String loginPage(String error, String logout, String expired, Model model) {
        if (error != null) {
            model.addAttribute("errorMessage", "Invalid username or password. Please try again.");
        }
        if (expired != null) {
            model.addAttribute("logoutMessage", "You have been signed out. Please sign in again, or contact your administrator.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "You have been logged out securely.");
        }
        return "auth/login";
    }

    @GetMapping("/signup")
    public String signupPage(Model model, RedirectAttributes redirectAttributes) {
        if (!signupEnabled) {
            redirectAttributes.addFlashAttribute("errorMessage", SIGNUP_CLOSED_MESSAGE);
            return SIGNUP_CLOSED_REDIRECT;
        }
        if (!model.containsAttribute("registerRequest")) {
            model.addAttribute("registerRequest", new RegisterRequest());
        }
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String processSignup(@Valid @ModelAttribute("registerRequest") RegisterRequest request,
                                BindingResult result,
                                RedirectAttributes redirectAttributes) {
        if (!signupEnabled) {
            redirectAttributes.addFlashAttribute("errorMessage", SIGNUP_CLOSED_MESSAGE);
            return SIGNUP_CLOSED_REDIRECT;
        }
        if (result.hasErrors()) {
            return "auth/signup";
        }

        String cleanedUsername = request.getUsername().trim().toLowerCase();
        String cleanedEmail = request.getEmail().trim().toLowerCase();
        String cleanedMobile = request.getMobileNumber().trim();

        // Unique checks across Username, Email, and Mobile
        if (userRepository.existsByUsername(cleanedUsername)) {
            result.rejectValue("username", "duplicate", "This username is already taken.");
        }

        if (userRepository.existsByEmail(cleanedEmail)) {
            result.rejectValue("email", "duplicate", "This email address is already registered.");
        }

        if (userRepository.existsByMobileNumber(cleanedMobile)) {
            result.rejectValue("mobileNumber", "duplicate", "This mobile number is already linked to another account.");
        }

        if (result.hasErrors()) {
            return "auth/signup";
        }

        User newAgent = User.builder()
                .fullname(request.getFullname().trim())
                .username(cleanedUsername)
                .email(cleanedEmail)
                .mobileNumber(cleanedMobile)
                .password(passwordEncoder.encode(request.getPassword()))
                .role("ROLE_AGENT")
                .build();

        userRepository.save(newAgent);

        redirectAttributes.addFlashAttribute("successMessage", "Agent account created successfully! Please login.");
        return "redirect:/staff-login";
    }
}
