package com.groupmatch.exception;

import java.util.UUID;

public class MeetingNotFoundException extends RuntimeException {
    public MeetingNotFoundException(UUID id) {
        super("Meeting not found: " + id);
    }
}
