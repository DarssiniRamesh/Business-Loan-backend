package com.example.demo.repository;

import com.example.demo.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

// PUBLIC_INTERFACE
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
