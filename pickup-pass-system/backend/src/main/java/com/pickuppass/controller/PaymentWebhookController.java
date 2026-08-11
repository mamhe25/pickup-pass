package com.pickuppass.controller;

import com.pickuppass.service.GenericHmacPaymentWebhookAdapter;
import com.pickuppass.service.PaymentWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/payments")
public class PaymentWebhookController {
    private final PaymentWebhookService service;
    public PaymentWebhookController(PaymentWebhookService service) { this.service = service; }

    @PostMapping("/{provider}")
    public ResponseEntity<?> receive(@PathVariable String provider,
                                     @RequestBody byte[] rawBody,
                                     HttpServletRequest request) {
        Map<String,String> headers = new HashMap<>();
        request.getHeaderNames().asIterator().forEachRemaining(name ->
                headers.put(name.toLowerCase(Locale.ROOT), request.getHeader(name)));
        try {
            return ResponseEntity.ok(service.handle(provider, headers, rawBody, Instant.now()));
        } catch (GenericHmacPaymentWebhookAdapter.WebhookSignatureException e) {
            return ResponseEntity.status(401).body(Map.of("error", "INVALID_SIGNATURE"));
        } catch (GenericHmacPaymentWebhookAdapter.WebhookUnavailableException e) {
            return ResponseEntity.status(503).body(Map.of("error", "WEBHOOK_NOT_CONFIGURED"));
        } catch (GenericHmacPaymentWebhookAdapter.WebhookValidationException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_WEBHOOK", "message", e.getMessage()));
        } catch (PaymentWebhookService.WebhookProcessingException e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", "PAYMENT_REJECTED", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "WEBHOOK_PROCESSING_FAILED"));
        }
    }
}
