package com.groupmatch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupmatch.domain.Plan;
import com.groupmatch.domain.Subscription;
import com.groupmatch.domain.SubscriptionStatus;
import com.groupmatch.domain.User;
import com.groupmatch.dto.payment.CreatePaymentRequest;
import com.groupmatch.dto.payment.CreatePaymentResponse;
import com.groupmatch.dto.payment.SubscriptionResponse;
import com.groupmatch.exception.UserNotFoundException;
import com.groupmatch.repository.SubscriptionRepository;
import com.groupmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class YooKassaService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${yookassa.shop-id:stub}")
    private String shopId;

    @Value("${yookassa.secret-key:stub}")
    private String secretKey;

    @Value("${app.mail.base-url:http://localhost:3000}")
    private String appBaseUrl;

    private static final String YOOKASSA_API = "https://api.yookassa.ru/v3/payments";

    @Transactional
    public CreatePaymentResponse createPayment(UUID userId, CreatePaymentRequest req) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        Plan plan = Plan.valueOf(req.plan().toUpperCase());
        if (plan == Plan.FREE) throw new IllegalArgumentException("Cannot purchase FREE plan");

        long amountKopecks = computeAmountKopecks(plan, req.periodMonths());

        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setPlan(plan);
        sub.setPeriodMonths(req.periodMonths());
        sub.setAmountKopecks(amountKopecks);
        sub.setStatus(SubscriptionStatus.PENDING);
        sub = subscriptionRepository.save(sub);

        if (isStub()) {
            log.info("YooKassa stub mode: skipping real payment. subscriptionId={}", sub.getId());
            return new CreatePaymentResponse(sub.getId(), appBaseUrl + "/pricing?stub=1", amountKopecks, "RUB");
        }

        try {
            String confirmationUrl = callYooKassaApi(sub, amountKopecks);
            return new CreatePaymentResponse(sub.getId(), confirmationUrl, amountKopecks, "RUB");
        } catch (Exception e) {
            log.error("YooKassa API error: {}", e.getMessage(), e);
            sub.setStatus(SubscriptionStatus.FAILED);
            subscriptionRepository.save(sub);
            throw new RuntimeException("Payment creation failed", e);
        }
    }

    @Transactional
    public void handleWebhook(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String event = root.path("event").asText();
            JsonNode object = root.path("object");
            String yookassaPaymentId = object.path("id").asText();

            log.info("YooKassa webhook received: event={}, paymentId={}", event, yookassaPaymentId);

            Optional<Subscription> subOpt = subscriptionRepository.findByYookassaPaymentId(yookassaPaymentId);
            if (subOpt.isEmpty()) {
                String subId = object.path("metadata").path("subscription_id").asText(null);
                if (subId != null) {
                    subOpt = subscriptionRepository.findById(UUID.fromString(subId));
                }
            }
            if (subOpt.isEmpty()) {
                log.warn("Subscription not found for yookassaPaymentId={}", yookassaPaymentId);
                return;
            }

            Subscription sub = subOpt.get();
            sub.setYookassaPaymentId(yookassaPaymentId);

            switch (event) {
                case "payment.succeeded" -> {
                    sub.setStatus(SubscriptionStatus.ACTIVE);
                    sub.setExpiresAt(Instant.now().plus(sub.getPeriodMonths() * 30L, ChronoUnit.DAYS));
                    subscriptionRepository.save(sub);
                    User user = sub.getUser();
                    user.setPlan(sub.getPlan());
                    userRepository.save(user);
                    log.info("Subscription activated. userId={}, plan={}, expires={}", user.getId(), sub.getPlan(), sub.getExpiresAt());
                }
                case "payment.canceled" -> {
                    sub.setStatus(SubscriptionStatus.CANCELLED);
                    subscriptionRepository.save(sub);
                    log.info("Subscription cancelled. subscriptionId={}", sub.getId());
                }
                default -> log.debug("Unhandled YooKassa event: {}", event);
            }
        } catch (Exception e) {
            log.error("Failed to process YooKassa webhook: {}", e.getMessage(), e);
            throw new RuntimeException("Webhook processing failed", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionResponse> getActiveSubscription(UUID userId) {
        return subscriptionRepository
                .findTopByUserIdAndStatusOrderByCreatedAtDesc(userId, SubscriptionStatus.ACTIVE)
                .map(this::toDto);
    }

    private boolean isStub() {
        return shopId == null || shopId.isBlank() || "stub".equalsIgnoreCase(shopId);
    }

    private long computeAmountKopecks(Plan plan, int periodMonths) {
        if (plan == Plan.PRO) {
            // 1490 RUB/year, 199 RUB/month
            return periodMonths >= 12 ? 149000L : 19900L * periodMonths;
        }
        return 0L;
    }

    private String callYooKassaApi(Subscription sub, long amountKopecks) throws Exception {
        String credentials = Base64.getEncoder().encodeToString(
                (shopId + ":" + secretKey).getBytes(StandardCharsets.UTF_8));

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "amount", Map.of(
                        "value", String.format("%.2f", amountKopecks / 100.0),
                        "currency", "RUB"
                ),
                "confirmation", Map.of(
                        "type", "redirect",
                        "return_url", appBaseUrl + "/profile"
                ),
                "description", "GroupMatch " + sub.getPlan().name() + " — " + sub.getPeriodMonths() + " мес.",
                "metadata", Map.of("subscription_id", sub.getId().toString()),
                "capture", true
        ));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(YOOKASSA_API))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/json")
                .header("Idempotence-Key", sub.getId().toString())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new RuntimeException("YooKassa API returned " + response.statusCode() + ": " + response.body());
        }

        JsonNode resp = objectMapper.readTree(response.body());
        sub.setYookassaPaymentId(resp.path("id").asText());
        subscriptionRepository.save(sub);

        return resp.path("confirmation").path("confirmation_url").asText();
    }

    private SubscriptionResponse toDto(Subscription s) {
        return new SubscriptionResponse(
                s.getId(), s.getPlan().name(), s.getStatus().name(),
                s.getAmountKopecks(), s.getPeriodMonths(), s.getExpiresAt(), s.getCreatedAt()
        );
    }
}
