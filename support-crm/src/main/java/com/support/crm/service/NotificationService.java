package com.support.crm.service;

import com.support.crm.model.TicketStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final BrevoEmailClient emailClient;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // 1. Ticket Creation Confirmation Email (English)
    @Async // <-- Ye method ko alag background thread me daal dega, API block nahi hogi!
    public void sendTicketCreatedNotification(String toEmail, String ticketRef, String subject) {
        emailClient.send(new EmailMessage(toEmail, "Ticket Logged Successfully: [" + ticketRef + "]",
                "Dear Customer,\n\n"
                        + "Thank you for reaching out. Your request has been registered.\n\n"
                        + "Ticket Reference ID: " + ticketRef + "\n"
                        + "Subject: " + subject + "\n\n"
                        + "You can check progress at any time using the link below, with the email address you used for this ticket:\n"
                        + trackingLink(ticketRef) + "\n\n"
                        + "Kind regards,\n"
                        + "SupportDesk Team"));
    }

    // 2. Ticket Update Email. It never says who changed the ticket or what they wrote.
    @Async
    public void sendTicketUpdatedNotification(String toEmail, String ticketRef, TicketStatus status) {
        emailClient.send(new EmailMessage(toEmail, "Your ticket was updated: [" + ticketRef + "]",
                "Dear Customer,\n\n"
                        + "There is an update on your ticket " + ticketRef + ".\n\n"
                        + "Current status: " + label(status) + "\n\n"
                        + "You can check it here, with the email address you used for this ticket:\n"
                        + trackingLink(ticketRef) + "\n\n"
                        + "Kind regards,\n"
                        + "SupportDesk Team"));
    }

    // 3. Ticket Resolution Alert Email (English)
    @Async
    public void sendTicketResolvedNotification(String toEmail, String ticketRef) {
        emailClient.send(new EmailMessage(toEmail, "Ticket Resolved: [" + ticketRef + "]",
                "Dear Customer,\n\n"
                        + "Your ticket " + ticketRef + " has been marked as resolved.\n\n"
                        + "You can review it here, with the email address you used for this ticket:\n"
                        + trackingLink(ticketRef) + "\n\n"
                        + "If the issue continues, you can submit a new request on our support portal.\n\n"
                        + "Thank you for your patience,\n"
                        + "SupportDesk Team"));
    }

    private String trackingLink(String ticketRef) {
        return baseUrl + "/portal/track?ref=" + ticketRef;
    }

    private String label(TicketStatus status) {
        String words = status.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
