package com.groupmatch.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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
    
    @ExceptionHandler(WebhookVerificationException.class)
    public ResponseEntity<ErrorResponse> handleWebhookVerification(WebhookVerificationException ex) {
        // Наружу — только код, без подробностей: не подсказываем, что именно не сошлось.
        return ResponseEntity.status(UNAUTHORIZED).body(
                new ErrorResponse("webhook_unauthorized", "Webhook verification failed", null, Instant.now()));
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

    @ExceptionHandler(FeedbackNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFeedbackNotFound(FeedbackNotFoundException ex) {
        return ResponseEntity.status(NOT_FOUND).body(
                new ErrorResponse("feedback_not_found", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(FORBIDDEN).body(
                new ErrorResponse("forbidden", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(BAD_REQUEST).body(
                new ErrorResponse("bad_request", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(BAD_REQUEST).body(
                new ErrorResponse("invalid_argument", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex,
                                                                    HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        // Отказ по валидации не оставлял в логах ничего. Для регистрации это
        // означало, что запрос с коротким паролем не отличим от запроса,
        // который вообще не дошёл: в обоих случаях в логе пусто.
        //
        // Пишутся имена полей и тексты ограничений — не значения. Значение
        // отклонённого поля (FieldError.getRejectedValue) здесь и есть пароль.
        log.info("Validation failed: {} {} fields={}",
                request.getMethod(), request.getRequestURI(), errors);

        ErrorResponse error = new ErrorResponse(
                "validation_failed",
                "Invalid input",
                errors,
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Всё, для чего нет своего обработчика.
     *
     * ⚠️ Сюда же попадают штатные ошибки Spring, у которых есть собственные
     * коды: несуществующий путь (должен быть 404), неверный метод (405), битое
     * тело запроса (400). Все они превращаются в 500 — это отдельная находка
     * аудита (`docs/audit-2026-09.md`, находка 2), и чинится она не здесь.
     *
     * Пока это так, строка ниже будет шумной: каждый чужой сканер даст ERROR
     * со стектрейсом. Это осознанный размен. Молчащий catch-all хуже: настоящее
     * падение уходило в ответ строкой «An unexpected error occurred» и не
     * оставляло следа нигде — ни стектрейса, ни даже упоминания, что что-то
     * произошло, потому что для Spring исключение считается обработанным.
     * Шум виден и заставит развести случаи; тишина не заставляет ничего.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: {} {} -> {}",
                request.getMethod(), request.getRequestURI(), ex.getClass().getName(), ex);

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