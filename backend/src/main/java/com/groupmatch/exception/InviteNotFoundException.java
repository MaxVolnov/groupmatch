package com.groupmatch.exception;

import java.util.UUID;

public class InviteNotFoundException extends RuntimeException {
    public InviteNotFoundException(UUID id) {
        super("Invite not found: " + id);
    }
    public InviteNotFoundException(String token) {
        super("Invite not found for token: " + token);
    }
}
