package com.groupmatch.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("User not found");
    }

    public UserNotFoundException(UUID id) {
        super("User not found: " + id);
    }
}
