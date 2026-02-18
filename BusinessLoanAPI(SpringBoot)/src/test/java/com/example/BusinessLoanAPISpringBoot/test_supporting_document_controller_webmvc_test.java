package com.example.BusinessLoanAPISpringBoot;

import com.example.BusinessLoanAPISpringBoot.auth.security.SecurityConfig;
import com.example.BusinessLoanAPISpringBoot.auth.service.JwtService;
import com.example.BusinessLoanAPISpringBoot.documents.api.SupportingDocumentController;
import com.example.BusinessLoanAPISpringBoot.documents.api.dto.DocumentDtos;
import com.example.BusinessLoanAPISpringBoot.documents.service.SupportingDocumentService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Maps to documented test cases (implemented endpoints):
 * - "Security: Only owner can view/delete their own documents" (authn guard + subject UUID handling)
 * - "Block disallowed file types with actionable message" (validated in SupportingDocumentService unit tests)
 */
@WebMvcTest(controllers = SupportingDocumentController.class)
@Import(SecurityConfig.class)
class test_supporting_document_controller_webmvc_test {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private SupportingDocumentService supportingDocumentService;

    private void stubJwt(String token, String subject, List<String> roles) {
        Claims claims = Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn(subject);
        when(claims.get("roles", List.class)).thenReturn(roles);
        when(jwtService.parseAndValidate(token)).thenReturn(claims);
    }

    @Test
    @DisplayName("POST /api/documents without JWT returns 401")
    void upload_withoutJwt_is401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "doc.pdf",
                "application/pdf",
                "hello".getBytes()
        );

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/documents with non-UUID JWT subject returns 400")
    void upload_withNonUuidSubject_is400() throws Exception {
        String token = "t-doc-1";
        stubJwt(token, "not-a-uuid", List.of("APPLICANT"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "doc.pdf",
                "application/pdf",
                "hello".getBytes()
        );

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MULTIPART_FORM_DATA_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"))
                .andExpect(jsonPath("$.message").value("JWT subject must be a UUID user id"));
    }

    @Test
    @DisplayName("POST /api/documents with valid JWT passes userId parsed from subject into service")
    void upload_withValidJwt_callsService() throws Exception {
        String token = "t-doc-2";
        UUID userId = UUID.randomUUID();
        stubJwt(token, userId.toString(), List.of("APPLICANT"));

        UUID draftId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../doc.pdf",
                "application/pdf",
                "hello".getBytes()
        );

        DocumentDtos.UploadResponse resp = new DocumentDtos.UploadResponse(
                UUID.randomUUID(),
                userId,
                draftId,
                "doc.pdf",
                "application/pdf",
                5L,
                "abc",
                "{}",
                Instant.parse("2026-01-01T00:00:00Z")
        );
        when(supportingDocumentService.upload(eq(userId), eq(draftId), any(), anyString())).thenReturn(resp);

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("loanDraftId", draftId.toString())
                        .param("metadata", "{\"docType\":\"BANK_STATEMENT\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.loanDraftId").value(draftId.toString()));

        ArgumentCaptor<String> metadataCaptor = ArgumentCaptor.forClass(String.class);
        verify(supportingDocumentService, times(1)).upload(eq(userId), eq(draftId), any(), metadataCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("{\"docType\":\"BANK_STATEMENT\"}", metadataCaptor.getValue());
    }
}
