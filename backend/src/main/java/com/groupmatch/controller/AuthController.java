package com.groupmatch.controller;

import com.groupmatch.dto.auth.*;
import com.groupmatch.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        UserResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signin(@Valid @RequestBody SigninRequest request) {
        AuthResponse response = authService.signin(request);
        return ResponseEntity.ok(response);
    }
}