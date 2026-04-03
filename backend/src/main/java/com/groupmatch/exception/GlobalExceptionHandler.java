package com.groupmatch.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        ErrorResponse error = new ErrorResponse(
                "email_already_exists",
                ex.getMessage(),
                null,
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
    
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        ErrorResponse error = new ErrorResponse(
                "invalid_credentials",
                ex.getMessage(),
                null,
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }
    
    @ExceptionHandler(GroupNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGroupNotFound(GroupNotFoundException ex) {
        return ResponseEntity.status(NOT_FOUND).body(
                new ErrorResponse("group_not_found", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(NOT_FOUND).body(
                new ErrorResponse("user_not_found", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(NotGroupMemberException.class)
    public ResponseEntity<ErrorResponse> handleNotGroupMember(NotGroupMemberException ex) {
        return ResponseEntity.status(FORBIDDEN).body(
                new ErrorResponse("not_group_member", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(NotGroupOwnerException.class)
    public ResponseEntity<ErrorResponse> handleNotGroupOwner(NotGroupOwnerException ex) {
        return ResponseEntity.status(FORBIDDEN).body(
                new ErrorResponse("not_group_owner", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(PlanLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handlePlanLimit(PlanLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(
                new ErrorResponse("plan_limit_exceeded", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(MemberAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleMemberAlreadyExists(MemberAlreadyExistsException ex) {
        return ResponseEntity.status(CONFLICT).body(
                new ErrorResponse("member_already_exists", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(MemberBannedException.class)
    public ResponseEntity<ErrorResponse> handleMemberBanned(MemberBannedException ex) {
        return ResponseEntity.status(FORBIDDEN).body(
                new ErrorResponse("member_banned", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(SlotNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSlotNotFound(SlotNotFoundException ex) {
        return ResponseEntity.status(NOT_FOUND).body(
                new ErrorResponse("slot_not_found", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(InviteNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInviteNotFound(InviteNotFoundException ex) {
        return ResponseEntity.status(NOT_FOUND).body(
                new ErrorResponse("invite_not_found", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(InviteInvalidException.class)
    public ResponseEntity<ErrorResponse> handleInviteInvalid(InviteInvalidException ex) {
        return ResponseEntity.status(GONE).body(
                new ErrorResponse("invite_invalid", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(MeetingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMeetingNotFound(MeetingNotFoundException ex) {
        return ResponseEntity.status(NOT_FOUND).body(
                new ErrorResponse("meeting_not_found", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(BAD_REQUEST).body(
                new ErrorResponse("invalid_argument", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        ErrorResponse error = new ErrorResponse(
                "validation_failed",
                "Invalid input",
                errors,
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        ErrorResponse error = new ErrorResponse(
                "server_error",
                "An unexpected error occurred",
                null,
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    record ErrorResponse(
            String code,
            String message,
            Object details,
            Instant timestamp
    ) {}
}