package com.support.crm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:support@crm.com}")
    private String fromEmail;

    // 1. Ticket Creation Confirmation Email (English)
    @Async // <-- Ye method ko alag background thread me daal dega, API block nahi hogi!
    public void sendTicketCreatedNotification(String toEmail, String ticketRef, String subject) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Ticket Logged Successfully: [" + ticketRef + "]");
            message.setText("Dear Customer,\n\n"
                    + "Thank you for reaching out. Your inquiry has been successfully registered in our system.\n\n"
                    + "Ticket Reference ID: " + ticketRef + "\n"
                    + "Subject: " + subject + "\n\n"
                    + "Our support team is actively reviewing your request. You can check real-time progress and notes at any time using the link below:\n"
                    + "http://localhost:8080/portal/track?ref=" + ticketRef + "\n\n"
                    + "Kind regards,\n"
                    + "SupportDesk Operations Team");

            mailSender.send(message);
            log.info("Ticket created confirmation email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send ticket created email to {}: {}", toEmail, e.getMessage());
        }
    }

    // 2. Ticket Resolution Alert Email (English)
    @Async // <-- Ye method ko alag background thread me daal dega, API block nahi hogi!
    public void sendTicketResolvedNotification(String toEmail, String ticketRef) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Ticket Resolved: [" + ticketRef + "]");
            message.setText("Dear Customer,\n\n"
                    + "Your inquiry with Reference ID #" + ticketRef + " has been marked as RESOLVED by our support operations team.\n\n"
                    + "You can review the complete resolution summary and agent timeline notes here:\n"
                    + "http://localhost:8080/portal/track?ref=" + ticketRef + "\n\n"
                    + "If your issue persists or requires further escalation, you may log a new ticket on our support portal.\n\n"
                    + "Thank you for your patience,\n"
                    + "SupportDesk Operations Team");

            mailSender.send(message);
            log.info("Ticket resolved email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send ticket resolved email to {}: {}", toEmail, e.getMessage());
        }
    }
}