package com.sapphireai.service.auth;

import com.sapphireai.model.dto.AuthResponse;
import com.sapphireai.model.dto.ChangePasswordRequest;
import com.sapphireai.model.dto.LoginRequest;
import com.sapphireai.model.dto.RegisterRequest;
import com.sapphireai.model.entity.Organization;
import com.sapphireai.model.entity.User;
import com.sapphireai.repository.OrganizationRepository;
import com.sapphireai.repository.UserRepository;
import com.sapphireai.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Authentication service: registration, login, organization management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if user exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        // Extract domain from email
        String domain = request.getEmail().split("@")[1];

        // Find or create organization
        Organization organization = organizationRepository.findByDomain(domain)
                .orElseGet(() -> createOrganization(domain, request.getOrganization()));

        // Create user
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .organization(request.getOrganization())
                .organizationId(organization.getId())
                .role("USER")
                .enabled(true)
                .build();

        userRepository.save(user);

        // Generate JWT
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtService.generateToken(userDetails);

        log.info("User registered: {} (org: {})", request.getEmail(), organization.getName());

        return AuthResponse.builder()
                .accessToken(token)
                .expiresIn(86400000L)  // 24 hours
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        // Authenticate
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        // Load user
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Generate JWT
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtService.generateToken(userDetails);

        log.info("User logged in: {}", request.getEmail());

        return AuthResponse.builder()
                .accessToken(token)
                .expiresIn(86400000L)
                .userId(user.getId())
                .email(user.getEmail())
                .mustChangePassword(user.getMustChangePassword())
                .role(user.getRole())
                .build();
    }

    private Organization createOrganization(String domain, String name) {
        Organization org = Organization.builder()
                .id(UUID.randomUUID().toString())
                .name(name != null ? name : domain)
                .domain(domain)
                .enabled(true)
                .build();

        return organizationRepository.save(org);
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New passwords do not match");
        }
        if (request.getNewPassword().length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        log.info("Password changed for user: {}", email);
    }
}