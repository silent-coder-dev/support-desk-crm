package com.support.crm.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreateTicketResponse {
    private String ticket_id;
    private LocalDateTime created_at;
    private boolean existing; // True when this request joined a ticket that is already open
}
