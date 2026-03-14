package com.sapphireai.controller;

import com.sapphireai.model.dto.CreateUserRequest;
import com.sapphireai.model.entity.User;
import com.sapphireai.model.dto.AdminStatsResponse;
import com.sapphireai.repository.UserRepository;
import com.sapphireai.service.admin.AdminStatsService;
import com.sapphireai.service.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;

import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final VectorStoreService vectorStoreService;
    private final JdbcTemplate jdbcTemplate;
    private final AdminStatsService adminStatsService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/reset-vectors")
    public ResponseEntity<String> resetVectors() {
        vectorStoreService.deleteCollection();
        return ResponseEntity.ok("Collection reset");
    }

    @PostMapping("/cleanup-test-data")
    public ResponseEntity<Map<String, Object>> cleanupTestData(
            @RequestParam String connectionId) {

        try {
            log.info("Cleaning up test data for connection: {}", connectionId);

            // Delete chunks
            int chunksDeleted = jdbcTemplate.update(
                    "DELETE FROM chunks WHERE document_id IN " +
                            "(SELECT id FROM documents WHERE id LIKE ?)",
                    "confluence_" + connectionId + "%"
            );

            // Delete documents
            int docsDeleted = jdbcTemplate.update(
                    "DELETE FROM documents WHERE id LIKE ?",
                    "confluence_" + connectionId + "%"
            );

            // Delete pages
            int pagesDeleted = jdbcTemplate.update(
                    "DELETE FROM confluence_pages WHERE connection_id = ?",
                    connectionId
            );

            // Delete attachments
            int attachmentsDeleted = jdbcTemplate.update(
                    "DELETE FROM confluence_attachments WHERE connection_id = ?",
                    connectionId
            );

            // Delete jobs
            int jobsDeleted = jdbcTemplate.update(
                    "DELETE FROM confluence_sync_jobs WHERE connection_id = ?",
                    connectionId
            );

            // Delete connection
            int connDeleted = jdbcTemplate.update(
                    "DELETE FROM confluence_connections WHERE id = ?",
                    connectionId
            );

            // Reset Qdrant
            vectorStoreService.deleteCollection();

            log.info("Clean-up complete: docs={}, chunks={}, pages={}, attachments={}, jobs={}, conn={}",
                    docsDeleted, chunksDeleted, pagesDeleted, attachmentsDeleted, jobsDeleted, connDeleted);

            return ResponseEntity.ok(Map.of(
                    "message", "Test data cleaned up successfully",
                    "documentsDeleted", docsDeleted,
                    "chunksDeleted", chunksDeleted,
                    "pagesDeleted", pagesDeleted,
                    "attachmentsDeleted", attachmentsDeleted,
                    "jobsDeleted", jobsDeleted,
                    "connectionDeleted", connDeleted
            ));

        } catch (Exception e) {
            log.error("Clean-up failed", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getStats(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        AdminStatsResponse stats = adminStatsService.getStats(user.getOrganizationId());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        User admin = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!"ADMIN".equals(admin.getRole())) {
            return ResponseEntity.status(403).body("Admin access required");
        }

        Page<User> users = userRepository.findByOrganizationId(
                admin.getOrganizationId(),
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );

        return ResponseEntity.ok(users.map(u -> Map.of(
                "id", u.getId(),
                "email", u.getEmail(),
                "fullName", u.getFullName() != null ? u.getFullName() : "",
                "role", u.getRole(),
                "enabled", u.getEnabled(),
                "mustChangePassword", u.getMustChangePassword() != null ? u.getMustChangePassword() : false,
                "createdAt", u.getCreatedAt().toString()
        )));
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(
            @RequestBody CreateUserRequest request,
            Authentication authentication
    ) {
        User admin = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!"ADMIN".equals(admin.getRole())) {
            return ResponseEntity.status(403).body("Admin access required");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already registered"));
        }

        // Generate temp password
        String tempPassword = generateTempPassword();

        User newUser = User.builder()
                .id(UUID.randomUUID().toString())
                .email(request.getEmail())
                .password(passwordEncoder.encode(tempPassword))
                .fullName(request.getFullName())
                .organizationId(admin.getOrganizationId())
                .role(request.getRole() != null ? request.getRole() : "USER")
                .enabled(true)
                .mustChangePassword(true)
                .build();

        userRepository.save(newUser);
        log.info("Admin {} created user: {}", admin.getEmail(), request.getEmail());

        return ResponseEntity.ok(Map.of(
                "id", newUser.getId(),
                "email", newUser.getEmail(),
                "tempPassword", tempPassword,
                "message", "User created. Share the temporary password with the user."
        ));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(
            @PathVariable String userId,
            Authentication authentication
    ) {
        User admin = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!"ADMIN".equals(admin.getRole())) {
            return ResponseEntity.status(403).body("Admin access required");
        }

        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!targetUser.getOrganizationId().equals(admin.getOrganizationId())) {
            return ResponseEntity.status(403).body("Cannot delete users from another organization");
        }

        if (targetUser.getEmail().equals(admin.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete your own account"));
        }

        userRepository.delete(targetUser);
        log.info("Admin {} deleted user: {}", admin.getEmail(), targetUser.getEmail());
        return ResponseEntity.noContent().build();
    }

    private String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
