package com.support.crm.dto;

import com.support.crm.model.TicketStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateTicketRequest {
    private TicketStatus status;

    @Size(max = 5000, message = "Note must be under 5000 characters")
    private String notes;

    @NotNull(message = "Version is required")
    private Long version; // Track entity version for optimistic locking
}
