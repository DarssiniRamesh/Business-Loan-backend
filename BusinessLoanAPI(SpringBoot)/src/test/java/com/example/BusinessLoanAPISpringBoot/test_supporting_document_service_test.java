package com.example.BusinessLoanAPISpringBoot;

import com.example.BusinessLoanAPISpringBoot.documents.api.dto.DocumentDtos;
import com.example.BusinessLoanAPISpringBoot.documents.model.SupportingDocument;
import com.example.BusinessLoanAPISpringBoot.documents.repo.SupportingDocumentRepository;
import com.example.BusinessLoanAPISpringBoot.documents.service.DocumentStorageService;
import com.example.BusinessLoanAPISpringBoot.documents.service.SupportingDocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Maps to documented test cases:
 * - "Accept only PDF/JPG/PNG via drag-and-drop and browse (FR-03)" => server-side contentType allowlist.
 * - "Block disallowed file types with actionable message"
 * - "Enforce max file size on client and server" (server-side max bytes)
 * - "View file metadata without download" (metadata JSON shape/normalization)
 * - "Security: Only owner can view/delete their own documents" (findByIdAndUserId ownership enforcement)
 *
 * Notes/gaps:
 * - Magic-number validation (MIME vs file signature) is NOT implemented in current SupportingDocumentService.
 * - Dedicated 413/415 HTTP statuses are NOT implemented; IllegalArgumentException maps to 400 via ApiExceptionHandler.
 */
@ExtendWith(MockitoExtension.class)
class test_supporting_document_service_test {

    @Test
    @DisplayName("upload rejects empty file (400 via controller layer)")
    void upload_emptyFile_rejected() {
        SupportingDocumentRepository repo = mock(SupportingDocumentRepository.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);

        SupportingDocumentService svc = new SupportingDocumentService(
                repo,
                storage,
                new ObjectMapper(),
                10,
                "application/pdf,image/jpeg,image/png"
        );

        MockMultipartFile file = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0]);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.upload(UUID.randomUUID(), null, file, "{}"));
        assertEquals("file is required", ex.getMessage());
    }

    @Test
    @DisplayName("upload rejects oversized file (max bytes enforced)")
    void upload_tooLarge_rejected() {
        SupportingDocumentRepository repo = mock(SupportingDocumentRepository.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);

        SupportingDocumentService svc = new SupportingDocumentService(
                repo,
                storage,
                new ObjectMapper(),
                3,
                "application/pdf,image/jpeg,image/png"
        );

        MockMultipartFile file = new MockMultipartFile("file", "x.pdf", "application/pdf", "hello".getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.upload(UUID.randomUUID(), null, file, "{}"));
        assertTrue(ex.getMessage().contains("file exceeds max size"));
    }

    @Test
    @DisplayName("upload rejects unsupported content type with message listing allowed types")
    void upload_unsupportedContentType_rejected() {
        SupportingDocumentRepository repo = mock(SupportingDocumentRepository.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);

        SupportingDocumentService svc = new SupportingDocumentService(
                repo,
                storage,
                new ObjectMapper(),
                10,
                "application/pdf,image/jpeg,image/png"
        );

        MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.upload(UUID.randomUUID(), null, file, "{}"));
        assertTrue(ex.getMessage().contains("Unsupported content type"));
        assertTrue(ex.getMessage().contains("application/pdf"));
    }

    @Test
    @DisplayName("upload normalizes metadata to JSON object string and sanitizes filename; computes sha256")
    void upload_happyPath_persistsMetadataAndChecksum() throws Exception {
        SupportingDocumentRepository repo = mock(SupportingDocumentRepository.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);

        // Important: digest is computed as the InputStream is read; our storage mock MUST read it.
        when(storage.write(any(), any(), any(InputStream.class))).thenAnswer(inv -> {
            InputStream in = inv.getArgument(2, InputStream.class);
            // Drain input to ensure DigestInputStream updates the MessageDigest.
            while (in.read() != -1) {
                // no-op
            }
            return "storage-key";
        });

        when(repo.save(any(SupportingDocument.class))).thenAnswer(inv -> inv.getArgument(0, SupportingDocument.class));

        SupportingDocumentService svc = new SupportingDocumentService(
                repo,
                storage,
                new ObjectMapper(),
                10_000,
                "application/pdf,image/jpeg,image/png"
        );

        UUID userId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../tax-return.pdf",
                "application/pdf",
                "hello".getBytes(StandardCharsets.UTF_8)
        );

        DocumentDtos.UploadResponse resp = svc.upload(userId, draftId, file, "{\"docType\":\"TAX_RETURN\"}");

        assertNotNull(resp.id());
        assertEquals(userId, resp.userId());
        assertEquals(draftId, resp.loanDraftId());
        assertEquals(".._tax-return.pdf", resp.originalFilename(), "Expected / replaced with _ in filename sanitation");
        assertEquals("application/pdf", resp.contentType());
        assertEquals(5L, resp.sizeBytes());
        assertNotNull(resp.createdAt());

        // SHA-256 hex should be 64 chars
        assertNotNull(resp.sha256Hex());
        assertEquals(64, resp.sha256Hex().length());

        // Metadata should remain a JSON object
        assertEquals("{\"docType\":\"TAX_RETURN\"}", resp.metadata());

        ArgumentCaptor<SupportingDocument> savedCaptor = ArgumentCaptor.forClass(SupportingDocument.class);
        verify(repo, times(1)).save(savedCaptor.capture());

        SupportingDocument saved = savedCaptor.getValue();
        assertEquals("storage-key", saved.getStorageKey());
        assertEquals(resp.sha256Hex(), saved.getSha256Hex());
    }

    @Test
    @DisplayName("upload rejects non-object metadata JSON")
    void upload_metadataMustBeObject() {
        SupportingDocumentRepository repo = mock(SupportingDocumentRepository.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);

        SupportingDocumentService svc = new SupportingDocumentService(
                repo,
                storage,
                new ObjectMapper(),
                10_000,
                "application/pdf,image/jpeg,image/png"
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "x.pdf",
                "application/pdf",
                "hello".getBytes(StandardCharsets.UTF_8)
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.upload(UUID.randomUUID(), null, file, "[]"));
        assertTrue(ex.getMessage().contains("metadata must be a JSON object string"));
    }

    @Test
    @DisplayName("getDownload enforces ownership via findByIdAndUserId (Document not found)")
    void download_notOwnedOrMissing_rejected() {
        SupportingDocumentRepository repo = mock(SupportingDocumentRepository.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);

        SupportingDocumentService svc = new SupportingDocumentService(
                repo,
                storage,
                new ObjectMapper(),
                10_000,
                "application/pdf,image/jpeg,image/png"
        );

        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(repo.findByIdAndUserId(docId, userId)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.getDownload(userId, docId));
        assertEquals("Document not found", ex.getMessage());
        verify(storage, never()).resolveToPath(anyString());
    }

    @Test
    @DisplayName("delete enforces ownership and calls storage.deleteIfExists")
    void delete_owned_callsStorageDelete() {
        SupportingDocumentRepository repo = mock(SupportingDocumentRepository.class);
        DocumentStorageService storage = mock(DocumentStorageService.class);

        SupportingDocumentService svc = new SupportingDocumentService(
                repo,
                storage,
                new ObjectMapper(),
                10_000,
                "application/pdf,image/jpeg,image/png"
        );

        UUID userId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        SupportingDocument doc = new SupportingDocument()
                .setId(docId)
                .setUserId(userId)
                .setOriginalFilename("x.pdf")
                .setContentType("application/pdf")
                .setSizeBytes(1L)
                .setSha256Hex("a".repeat(64))
                .setStorageKey("k1")
                .setMetadata("{}")
                .setCreatedAt(Instant.now());

        when(repo.findByIdAndUserId(docId, userId)).thenReturn(Optional.of(doc));

        svc.delete(userId, docId);

        verify(repo, times(1)).delete(doc);
        verify(storage, times(1)).deleteIfExists("k1");
    }
}
