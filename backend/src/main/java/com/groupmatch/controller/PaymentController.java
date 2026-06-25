package com.groupmatch.controller;

import com.groupmatch.dto.payment.CreatePaymentRequest;
import com.groupmatch.dto.payment.CreatePaymentResponse;
import com.groupmatch.dto.payment.SubscriptionResponse;
import com.groupmatch.security.UserPrincipal;
import com.groupmatch.service.YooKassaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final YooKassaService yooKassaService;

    @PostMapping("/yookassa/create")
    public CreatePaymentResponse createPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreatePaymentRequest req) {
        return yooKassaService.createPayment(principal.getId(), req);
    }

    @PostMapping("/yookassa/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String body) {
        yooKassaService.handleWebhook(body);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/subscription")
    public ResponseEntity<SubscriptionResponse> getSubscription(
            @AuthenticationPrincipal UserPrincipal principal) {
        return yooKassaService.getActiveSubscription(principal.getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
