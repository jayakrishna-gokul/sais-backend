package com.sapphireai.controller;

import com.sapphireai.config.Audited;
import com.sapphireai.model.dto.AuthResponse;
import com.sapphireai.model.dto.LoginRequest;
import com.sapphireai.model.dto.RegisterRequest;
import com.sapphireai.model.entity.audit.AuditEvent;
import com.sapphireai.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    @Audited(eventType = AuditEvent.EventType.AUTH, resourceType = "USER")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}