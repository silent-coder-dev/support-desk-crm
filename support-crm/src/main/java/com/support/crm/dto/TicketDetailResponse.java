package com.support.crm.dto;

import com.support.crm.model.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TicketDetailResponse {
    private String ticket_id;
    private String customer_name;
    private String customer_email;
    private String subject;
    private String description;
    private TicketStatus status;
    private Long version;
    private Long assigned_agent_id;
    private String assigned_agent_name;
    private List<NoteDto> notes;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class NoteDto {
        private String note_text;
        private LocalDateTime created_at;
        private String author_name;
    }
}