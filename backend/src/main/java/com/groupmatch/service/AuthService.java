package com.groupmatch.service;

import com.groupmatch.domain.User;
import com.groupmatch.dto.auth.*;
import com.groupmatch.exception.EmailAlreadyExistsException;
import com.groupmatch.exception.InvalidCredentialsException;
import com.groupmatch.repository.UserRepository;
import com.groupmatch.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    
    @Transactional
    public UserResponse signup(SignupRequest request) {
        log.info("Signup attempt for email: {}", request.email());
        
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email already registered");
        }
        
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setTzid(request.tzid() != null ? request.tzid() : "Europe/Moscow");
        user.setPlan("FREE");
        user.setIsBlocked(false);
        
        user = userRepository.save(user);
        log.info("User created successfully: userId={}", user.getId());
        
        return UserResponse.from(user);
    }
    
    @Transactional(readOnly = true)
    public AuthResponse signin(SigninRequest request) {
        log.info("Signin attempt for email: {}", request.email());
        
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        
        if (user.getIsBlocked()) {
            throw new InvalidCredentialsException("Account is blocked");
        }
        
        String accessToken = jwtUtils.generateAccessToken(user.getId(), user.getEmail(), user.getPlan());
        
        log.info("User signed in successfully: userId={}", user.getId());
        
        return new AuthResponse(accessToken, 900L); // 15 minutes in seconds
    }
}