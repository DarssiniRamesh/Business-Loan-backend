package com.example.BusinessLoanAPISpringBoot.documents.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;

/**
 * Filesystem storage strategy for document bytes.
 *
 * Notes:
 * - Stores files under {storageRoot}/{userId}/{documentId}/content
 * - Returns a storageKey relative to storageRoot for DB persistence.
 */
@Service
public class DocumentStorageService {

    private final Path storageRoot;

    public DocumentStorageService(@Value("${app.documents.storage-root}") String storageRoot) {
        this.storageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    // PUBLIC_INTERFACE
    public String write(UUID userId, UUID documentId, InputStream in) {
        /** Persist bytes to disk and return a storage key (relative path) suitable for DB persistence. */
        String storageKey = userId + "/" + documentId + "/content";
        Path target = storageRoot.resolve(storageKey).normalize();

        // Prevent path traversal in case of unexpected path manipulation.
        if (!target.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Invalid storage path");
        }

        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return storageKey;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store document bytes");
        }
    }

    // PUBLIC_INTERFACE
    public Path resolveToPath(String storageKey) {
        /** Resolve a previously stored storageKey to an absolute filesystem path. */
        Path p = storageRoot.resolve(storageKey).normalize();
        if (!p.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        return p;
    }

    // PUBLIC_INTERFACE
    public void deleteIfExists(String storageKey) {
        /** Delete the stored bytes if present (best-effort). */
        Path p = resolveToPath(storageKey);
        try {
            Files.deleteIfExists(p);
            // Attempt to clean up empty parent directory {userId}/{documentId}
            Path parent = p.getParent();
            if (parent != null) {
                try {
                    Files.deleteIfExists(parent);
                } catch (IOException ignored) {
                    // Directory not empty or in use; ignore.
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete stored document bytes");
        }
    }
}
