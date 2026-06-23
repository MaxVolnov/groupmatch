package com.groupmatch.controller;

import com.groupmatch.dto.admin.AdminUsersPageResponse;
import com.groupmatch.dto.admin.BanUserRequest;
import com.groupmatch.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/ping")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of("status", "ok", "role", "ADMIN"));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminUsersPageResponse> getUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminService.getUsers(search, page, size));
    }

    @PatchMapping("/users/{id}/ban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> banUser(
            @PathVariable UUID id,
            @Valid @RequestBody BanUserRequest request
    ) {
        adminService.banUser(id, request.reason());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{id}/unban")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> unbanUser(@PathVariable UUID id) {
        adminService.unbanUser(id);
        return ResponseEntity.noContent().build();
    }
}
