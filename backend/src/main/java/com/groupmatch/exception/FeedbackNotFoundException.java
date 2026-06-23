package com.groupmatch.exception;

import java.util.UUID;

public class FeedbackNotFoundException extends RuntimeException {
    public FeedbackNotFoundException() { super("Feedback not found"); }
    public FeedbackNotFoundException(UUID id) { super("Feedback not found: " + id); }
}
