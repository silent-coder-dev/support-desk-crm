package com.support.crm.service;

import com.support.crm.dto.CreateStaffRequest;
import com.support.crm.model.User;
import com.support.crm.repository.TicketRepository;
import com.support.crm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Admins add staff here. Public signup stays off, because every agent can read all customer tickets.
@Service
@RequiredArgsConstructor
public class StaffService {

    // Look-alike characters (0/O, 1/l/I) are left out, because this password is read and passed on by a person
    private static final String PASSWORD_CHARACTERS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RANDOM_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionRegistry sessionRegistry;
    private final SecureRandom random = new SecureRandom();

    // 1. Everyone with a login, by name
    @Transactional(readOnly = true)
    public List<User> getAllStaff() {
        return userRepository.findAllByOrderByFullnameAsc();
    }

    // 2. How many active tickets each person holds, by user ID
    @Transactional(readOnly = true)
    public Map<Long, Long> getActiveTicketCounts() {
        return ticketRepository.countActiveTicketsPerAgentId().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    // 3. Add a staff member. Returns the temporary password, which is only ever available here.
    @Transactional
    public String createStaff(CreateStaffRequest request) {
        String temporaryPassword = generatePassword();
        userRepository.save(User.builder()
                .fullname(request.getFullname().trim())
                .username(request.getUsername().trim().toLowerCase())
                .email(request.getEmail().trim().toLowerCase())
                .mobileNumber(emptyToNull(request.getMobileNumber()))
                .role(request.getRole())
                .password(passwordEncoder.encode(temporaryPassword))
                .build());
        return temporaryPassword;
    }

    // 4. Block or restore a login. Blocking also ends any session that is already open.
    @Transactional
    public User setDisabled(Long userId, boolean disabled, String adminUsername) {
        User member = findStaff(userId);
        if (member.getUsername().equals(adminUsername)) {
            throw new IllegalArgumentException("You can't disable your own account.");
        }
        member.setDisabled(disabled);
        if (disabled) {
            endSessions(member.getUsername());
        }
        return member;
    }

    // 5. Give someone a new temporary password, and sign them out everywhere
    @Transactional
    public String resetPassword(Long userId, String adminUsername) {
        User member = findStaff(userId);
        if (member.getUsername().equals(adminUsername)) {
            throw new IllegalArgumentException("Change your own password from your profile.");
        }
        String temporaryPassword = generatePassword();
        member.setPassword(passwordEncoder.encode(temporaryPassword));
        endSessions(member.getUsername());
        return temporaryPassword;
    }

    public User findStaff(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Staff member not found."));
    }

    private void endSessions(String username) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof UserDetails details && details.getUsername().equals(username))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .forEach(SessionInformation::expireNow);
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    // The fixed start guarantees the password rule is met: upper, lower, number and special character
    private String generatePassword() {
        StringBuilder password = new StringBuilder("Temp@1a");
        for (int i = 0; i < RANDOM_PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_CHARACTERS.charAt(random.nextInt(PASSWORD_CHARACTERS.length())));
        }
        return password.toString();
    }
}
