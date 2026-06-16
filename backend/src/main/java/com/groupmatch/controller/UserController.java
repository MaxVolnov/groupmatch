package com.groupmatch.controller;

import com.groupmatch.dto.auth.UpdateProfileRequest;
import com.groupmatch.dto.auth.UserResponse;
import com.groupmatch.security.UserPrincipal;
import com.groupmatch.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public UserResponse getMe(@AuthenticationPrincipal UserPrincipal principal) {
        return userService.getMe(principal.getId());
    }

    @PatchMapping
    public UserResponse updateMe(@AuthenticationPrincipal UserPrincipal principal,
                                 @Valid @RequestBody UpdateProfileRequest req) {
        return userService.updateMe(principal.getId(), req);
    }
}
