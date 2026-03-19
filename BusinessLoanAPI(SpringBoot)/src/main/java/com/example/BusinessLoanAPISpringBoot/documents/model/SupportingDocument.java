package com.example.BusinessLoanAPISpringBoot.documents.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Supporting document metadata persisted per user.
 *
 * File contents are stored separately (filesystem storage strategy) and referenced via storageKey.
 */
@Entity
@Table(name = "supporting_document")
public class SupportingDocument {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "loan_draft_id")
    private UUID loanDraftId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    // SHA-256 hex digest (64 chars). Stored as VARCHAR(64) in Postgres for compatibility with Hibernate validation.
    @Column(name = "sha256_hex", nullable = false, length = 64)
    private String sha256Hex;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    /**
     * JSON metadata stored as jsonb in Postgres; mapped as String to avoid extra dependencies.
     */
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public SupportingDocument setId(UUID id) {
        this.id = id;
        return this;
    }

    public UUID getUserId() {
        return userId;
    }

    public SupportingDocument setUserId(UUID userId) {
        this.userId = userId;
        return this;
    }

    public UUID getLoanDraftId() {
        return loanDraftId;
    }

    public SupportingDocument setLoanDraftId(UUID loanDraftId) {
        this.loanDraftId = loanDraftId;
        return this;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public SupportingDocument setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
        return this;
    }

    public String getContentType() {
        return contentType;
    }

    public SupportingDocument setContentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public SupportingDocument setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
        return this;
    }

    public String getSha256Hex() {
        return sha256Hex;
    }

    public SupportingDocument setSha256Hex(String sha256Hex) {
        this.sha256Hex = sha256Hex;
        return this;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public SupportingDocument setStorageKey(String storageKey) {
        this.storageKey = storageKey;
        return this;
    }

    public String getMetadata() {
        return metadata;
    }

    public SupportingDocument setMetadata(String metadata) {
        this.metadata = metadata;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public SupportingDocument setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }
}
