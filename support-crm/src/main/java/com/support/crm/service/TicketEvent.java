package com.support.crm.service;

import com.support.crm.model.TicketStatus;

// Published by TicketService and handled by TicketEventListener once the database commit succeeds
public record TicketEvent(Type type, String ticketId, String customerEmail, String subject, TicketStatus status) {

    public enum Type {
        CREATED,        // New ticket: confirmation email
        UPDATED,        // Live refresh only, no email
        CUSTOMER_UPDATE, // Staff changed the status or added a note: update email
        RESOLVED        // Resolution email
    }
}
