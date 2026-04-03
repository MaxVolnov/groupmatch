package com.groupmatch.exception;

import java.util.UUID;

public class SlotNotFoundException extends RuntimeException {
    public SlotNotFoundException(UUID id) {
        super("Availability slot not found: " + id);
    }
}
