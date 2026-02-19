package com.example.BusinessLoanAPISpringBoot.documents.service;

import com.example.BusinessLoanAPISpringBoot.documents.api.dto.DocumentDtos;
import com.example.BusinessLoanAPISpringBoot.documents.model.SupportingDocument;
import com.example.BusinessLoanAPISpringBoot.documents.repo.SupportingDocumentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Business logic for supporting documents:
 * - validate file type and size
 * - store bytes using DocumentStorageService
 * - persist metadata in Postgres
 * - enforce ownership (userId from JWT subject)
 */
@Service
public class SupportingDocumentService {

    private final SupportingDocumentRepository repo;
    private final DocumentStorageService storage;
    private final ObjectMapper objectMapper;

    private final long maxBytes;
    private final Set<String> allowedContentTypes;

    public SupportingDocumentService(
            SupportingDocumentRepository repo,
            DocumentStorageService storage,
            ObjectMapper objectMapper,
            @Value("${app.documents.max-bytes}") long maxBytes,
            @Value("${app.documents.allowed-content-types}") String allowedContentTypesCsv
    ) {
        this.repo = repo;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.maxBytes = maxBytes;
        this.allowedContentTypes = Arrays.stream(allowedContentTypesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    // PUBLIC_INTERFACE
    @Transactional
    public DocumentDtos.UploadResponse upload(UUID userId, UUID loanDraftId, MultipartFile file, String metadataJson) {
        /** Upload a document (PDF/JPG/PNG) for the authenticated user and persist metadata. */
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("file exceeds max size of " + maxBytes + " bytes");
        }

        String contentType = file.getContentType();
        if (contentType == null || !allowedContentTypes.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported content type. Allowed: " + String.join(", ", allowedContentTypes));
        }

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "document";
        }

        String normalizedMetadata = normalizeJsonObjectString(metadataJson == null ? "{}" : metadataJson, "metadata");

        UUID documentId = UUID.randomUUID();
        Instant now = Instant.now();

        // Compute checksum while streaming into storage
        String sha256Hex;
        String storageKey;
        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dis = new DigestInputStream(in, digest)) {
                storageKey = storage.write(userId, documentId, dis);
            }
            sha256Hex = bytesToHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to process upload");
        }

        SupportingDocument doc = new SupportingDocument()
                .setId(documentId)
                .setUserId(userId)
                .setLoanDraftId(loanDraftId)
                .setOriginalFilename(originalFilename)
                .setContentType(contentType)
                .setSizeBytes(file.getSize())
                .setSha256Hex(sha256Hex)
                .setStorageKey(storageKey)
                .setMetadata(normalizedMetadata)
                .setCreatedAt(now);

        SupportingDocument saved = repo.save(doc);

        return new DocumentDtos.UploadResponse(
                saved.getId(),
                saved.getUserId(),
                saved.getLoanDraftId(),
                saved.getOriginalFilename(),
                saved.getContentType(),
                saved.getSizeBytes(),
                saved.getSha256Hex(),
                saved.getMetadata(),
                saved.getCreatedAt()
        );
    }

    // PUBLIC_INTERFACE
    @Transactional
    public List<DocumentDtos.ListItem> list(UUID userId, UUID loanDraftId) {
        /** List documents for the authenticated user, optionally filtered by loanDraftId. */
        List<SupportingDocument> docs = (loanDraftId == null)
                ? repo.findAllByUserIdOrderByCreatedAtDesc(userId)
                : repo.findAllByUserIdAndLoanDraftIdOrderByCreatedAtDesc(userId, loanDraftId);

        return docs.stream().map(this::toListItem).toList();
    }

    // PUBLIC_INTERFACE
    @Transactional
    public DocumentDtos.ListItem updateMetadata(UUID userId, UUID documentId, String metadataJson) {
        /** Replace metadata JSON for an owned document. */
        SupportingDocument doc = repo.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        doc.setMetadata(normalizeJsonObjectString(metadataJson, "metadata"));
        SupportingDocument saved = repo.save(doc);
        return toListItem(saved);
    }

    // PUBLIC_INTERFACE
    @Transactional
    public ResourceDownload getDownload(UUID userId, UUID documentId) {
        /** Resolve the file resource for download, only if owned by the authenticated user. */
        SupportingDocument doc = repo.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        FileSystemResource resource = new FileSystemResource(storage.resolveToPath(doc.getStorageKey()));
        if (!resource.exists() || !resource.isReadable()) {
            throw new IllegalArgumentException("Document content not found");
        }

        return new ResourceDownload(resource, doc.getOriginalFilename(), doc.getContentType(), doc.getSizeBytes());
    }

    // PUBLIC_INTERFACE
    @Transactional
    public void delete(UUID userId, UUID documentId) {
        /** Delete document metadata and stored bytes, only if owned by the authenticated user. */
        SupportingDocument doc = repo.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        repo.delete(doc);
        storage.deleteIfExists(doc.getStorageKey());
    }

    private DocumentDtos.ListItem toListItem(SupportingDocument d) {
        return new DocumentDtos.ListItem(
                d.getId(),
                d.getUserId(),
                d.getLoanDraftId(),
                d.getOriginalFilename(),
                d.getContentType(),
                d.getSizeBytes() == null ? 0L : d.getSizeBytes(),
                d.getSha256Hex(),
                d.getMetadata(),
                d.getCreatedAt()
        );
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return null;
        }
        // Keep simple: strip path separators and control characters
        String cleaned = filename.replace("\\", "_").replace("/", "_");
        cleaned = cleaned.replaceAll("[\\p{Cntrl}]", "");
        return cleaned.length() > 255 ? cleaned.substring(0, 255) : cleaned;
    }

    private String normalizeJsonObjectString(String json, String fieldName) {
        if (json == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException(fieldName + " must be a JSON object string");
            }
            ObjectNode obj = (ObjectNode) node;
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(fieldName + " must be valid JSON: " + e.getOriginalMessage());
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Result wrapper for download behavior.
     */
    public record ResourceDownload(Resource resource, String filename, String contentType, long sizeBytes) {
    }
}
