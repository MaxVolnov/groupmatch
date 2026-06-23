package com.groupmatch.service;

import com.groupmatch.domain.Role;
import com.groupmatch.domain.User;
import com.groupmatch.dto.admin.AdminUserResponse;
import com.groupmatch.dto.admin.AdminUsersPageResponse;
import com.groupmatch.exception.ForbiddenException;
import com.groupmatch.exception.UserNotFoundException;
import com.groupmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;

    public AdminUsersPageResponse getUsers(String search, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> result = (search == null || search.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.findByEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                        search, search, pageable);
        return new AdminUsersPageResponse(
                result.getContent().stream().map(this::toDto).toList(),
                result.getNumber(),
                result.getTotalPages(),
                result.getTotalElements()
        );
    }

    @Transactional
    public void banUser(UUID userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        if (user.getRole() == Role.ADMIN) {
            throw new ForbiddenException("Cannot ban ADMIN");
        }
        user.setBanned(true);
        user.setBanReason(reason);
        userRepository.save(user);
        log.info("User banned. userId={}, reason={}", userId, reason);
    }

    @Transactional
    public void unbanUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        user.setBanned(false);
        user.setBanReason(null);
        userRepository.save(user);
        log.info("User unbanned. userId={}", userId);
    }

    private AdminUserResponse toDto(User u) {
        return new AdminUserResponse(
                u.getId(), u.getEmail(), u.getDisplayName(),
                u.getRole(), u.getPlan(), u.isGuest(), u.isBanned(), u.getCreatedAt()
        );
    }
}
