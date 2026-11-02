package com.support.crm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TicketEventListener {

    private final SseNotificationService sseNotificationService;
    private final NotificationService notificationService;

    // Runs only after the commit, so nobody hears about a change that was rolled back
    @TransactionalEventListener
    public void onTicketEvent(TicketEvent event) {
        sseNotificationService.broadcast("TICKET_UPDATED", event.ticketId());

        switch (event.type()) {
            case CREATED -> notificationService.sendTicketCreatedNotification(
                    event.customerEmail(), event.ticketId(), event.subject());
            case CUSTOMER_UPDATE -> notificationService.sendTicketUpdatedNotification(
                    event.customerEmail(), event.ticketId(), event.status());
            case RESOLVED -> notificationService.sendTicketResolvedNotification(
                    event.customerEmail(), event.ticketId());
            case UPDATED -> { }
        }
    }
}
