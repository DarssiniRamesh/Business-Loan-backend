package com.example.demo.controller;

import com.example.demo.dto.AuthRequests.*;
import com.example.demo.dto.AuthResponses.AuthResponse;
import com.example.demo.model.User;
import com.example.demo.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for registration, verification, and login")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) { this.authService = authService; }

    // PUBLIC_INTERFACE
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Register new user and send email verification", description = "User must supply email and password; a single-use, expiring verification email is sent. Login is blocked until verified.")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest req, HttpServletRequest servlet) {
        String ip = extractIp(servlet);
        try {
            authService.registerUser(req.email, req.password, ip);
            return ResponseEntity.ok(new AuthResponse(true, "Registration successful. Please verify your email.", null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new AuthResponse(false, "User already exists.", "EMAIL_EXISTS"));
        }
    }

    // PUBLIC_INTERFACE
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Login for verified users", description = "Login fails until email is verified. On success, issues a session (TODO: token/JWT).")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest req, HttpServletRequest servlet) {
        String ip = extractIp(servlet);
        boolean ok = authService.canLogin(req.email, req.password, ip);
        if (!ok) {
            Optional<User> user = authService.getUserByEmail(req.email);
            if (user.isPresent() && !user.get().isEmailVerified()) {
                return ResponseEntity.status(403).body(new AuthResponse(false, "Email not verified. Please verify via link or resend verification.", "EMAIL_NOT_VERIFIED"));
            }
            return ResponseEntity.status(401).body(new AuthResponse(false, "Invalid credentials.", "AUTH_FAILED"));
        }
        return ResponseEntity.ok(new AuthResponse(true, "Login successful.", null));
    }

    // PUBLIC_INTERFACE
    @PostMapping(value = "/resend-verification", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Resend email verification link", description = "Sends new verification email/link to user if unverified.")
    public ResponseEntity<AuthResponse> resendVerification(@RequestBody ResendVerificationRequest req, HttpServletRequest servlet) {
        Optional<User> user = authService.getUserByEmail(req.email);
        String ip = extractIp(servlet);
        if (user.isPresent() && !user.get().isEmailVerified()) {
            authService.sendOrResendVerification(user.get(), ip, false);
            return ResponseEntity.ok(new AuthResponse(true, "Verification email resent.", null));
        } else {
            return ResponseEntity.badRequest().body(new AuthResponse(false, "Invalid or already verified email.", "NO_ACTION"));
        }
    }

    // PUBLIC_INTERFACE
    @PostMapping(value = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consume verification link to activate account", description = "Verifies account by consuming token. Single-use, error for already used/expired.")
    public ResponseEntity<AuthResponse> verify(@RequestBody VerifyLinkRequest req, HttpServletRequest servlet) {
        String ip = extractIp(servlet);
        boolean ok = authService.verifyToken(req.token, ip);
        if (ok) return ResponseEntity.ok(new AuthResponse(true, "Verification successful. You may now login.", null));
        return ResponseEntity.status(400).body(new AuthResponse(false, "Invalid or expired/used verification link.", "VERIFICATION_INVALID"));
    }

    private String extractIp(HttpServletRequest req) {
        String xfwd = req.getHeader("X-Forwarded-For");
        if (xfwd != null && !xfwd.isBlank()) return xfwd.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
