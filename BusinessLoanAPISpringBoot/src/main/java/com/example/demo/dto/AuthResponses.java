package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class AuthResponses {
    // PUBLIC_INTERFACE
    @Schema(description = "Standard authentication response")
    public static class AuthResponse {
        public boolean success;
        public String message;
        public String errorCode;

        public AuthResponse() {}
        public AuthResponse(boolean success, String message, String errorCode) {
            this.success = success;
            this.message = message;
            this.errorCode = errorCode;
        }
    }
}
