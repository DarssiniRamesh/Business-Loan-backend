package com.example.demo.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// PUBLIC_INTERFACE
@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 48, nullable = false)
    private String action; // e.g., REGISTER, LOGIN_ATTEMPT, VERIFICATION_SENT

    @Column
    private String email; // May be null for non-email actions

    @Column(nullable = false)
    private String sourceIp;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column
    private String meta; // Optional details (json msg, result, etc.)

    public AuditLog() {}

    public AuditLog(String action, String email, String sourceIp, String meta) {
        this.action = action;
        this.email = email;
        this.sourceIp = sourceIp;
        this.meta = meta;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and setters omitted for brevity

    public Long getId() { return id; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getMeta() { return meta; }
    public void setMeta(String meta) { this.meta = meta; }
}
