package com.support.crm.repository;

import com.support.crm.model.TicketAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketAuditLogRepository extends JpaRepository<TicketAuditLog , Long> {

    // Dashboard par dikhane ke liye: Sabse latest 8 activities (newest first)
    List<TicketAuditLog> findTop8ByOrderByTimestampDesc();

    // Profile page par dikhane ke liye: Is user ki latest 5 activities
    List<TicketAuditLog> findTop5ByUserIdOrderByTimestampDesc(Long userId);
}
