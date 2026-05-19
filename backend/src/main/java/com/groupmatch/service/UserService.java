package com.groupmatch.service;

import com.groupmatch.domain.User;
import com.groupmatch.dto.auth.UpdateProfileRequest;
import com.groupmatch.dto.auth.UserResponse;
import com.groupmatch.exception.UserNotFoundException;
import com.groupmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateMe(UUID userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (req.displayName() != null) {
            user.setDisplayName(req.displayName());
        }
        if (req.tzId() != null) {
            user.setTzId(req.tzId());
        }

        return UserResponse.from(userRepository.save(user));
    }
}
