package com.pickuppass.controller;

import com.pickuppass.dto.QrVerificationResult;
import com.pickuppass.exception.ConflictException;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.IdempotencyService;
import com.pickuppass.service.PickupMetricsService;
import com.pickuppass.service.PushNotificationService;
import com.pickuppass.service.QrVerificationService;
import com.pickuppass.service.SubscriptionFeatureService;
import com.pickuppass.service.TenantUsageService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PickupConcurrencyTest {

    @Test
    void simultaneousApprovalsProduceExactlyOneReleaseSideEffect() throws Exception {
        QrVerificationService qr = mock(QrVerificationService.class);
        PushNotificationService push = mock(PushNotificationService.class);
        AuditService audit = mock(AuditService.class);
        PickupMetricsService metrics = mock(PickupMetricsService.class);
        IdempotencyService idempotency = mock(IdempotencyService.class);
        SubscriptionFeatureService features = mock(SubscriptionFeatureService.class);
        TenantUsageService usage = mock(TenantUsageService.class);
        PickupController controller = new PickupController(qr, push, audit, metrics, idempotency, features, usage);
        FirebaseUserDetails staff = new FirebaseUserDetails("staff1", "staff@test.com", "school1", "teacher");
        PickupController.VerifyRequest request = new PickupController.VerifyRequest();
        request.setQrToken("same-live-token");
        QrVerificationResult verified = QrVerificationResult.success("student1", "guardian1", null);

        when(idempotency.fingerprint("same-live-token\n")).thenReturn("fp-concurrent");
        when(idempotency.findExisting(eq("school1"), eq("staff1"), eq("pickup.approve"),
                anyString(), eq("fp-concurrent"))).thenReturn(Optional.empty());
        when(qr.verify("same-live-token", "school1")).thenReturn(verified);

        CountDownLatch bothAtTransaction = new CountDownLatch(2);
        AtomicBoolean releaseWon = new AtomicBoolean(false);
        when(qr.markUsedAndLog(verified, "staff1", "school1", null)).thenAnswer(invocation -> {
            bothAtTransaction.countDown();
            if (!bothAtTransaction.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent approval test did not overlap");
            }
            if (releaseWon.compareAndSet(false, true)) return "winning-log";
            throw new ConflictException("QR code was already used or superseded");
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> approveOutcome(controller, request, "request-a", staff));
            Future<Object> second = executor.submit(() -> approveOutcome(controller, request, "request-b", staff));
            Object firstOutcome = first.get(10, TimeUnit.SECONDS);
            Object secondOutcome = second.get(10, TimeUnit.SECONDS);

            long successes = Stream.of(firstOutcome, secondOutcome)
                    .filter(ResponseEntity.class::isInstance)
                    .map(ResponseEntity.class::cast)
                    .filter(response -> response.getStatusCode().is2xxSuccessful())
                    .count();
            long conflicts = Stream.of(firstOutcome, secondOutcome)
                    .filter(ConflictException.class::isInstance)
                    .count();

            assertEquals(1, successes);
            assertEquals(1, conflicts);
            verify(push, times(1)).notifyGuardiansOfPickup("student1", "guardian1");
            verify(audit, times(1)).record(
                    eq(staff), eq("pickup.approved"), eq("exitLog"), eq("winning-log"), anyMap());
            verify(idempotency, times(1)).storeResult(
                    eq("school1"), eq("staff1"), eq("pickup.approve"),
                    anyString(), eq("fp-concurrent"), eq("winning-log"));
            verify(usage, times(1)).recordQrPickup("school1");
        } finally {
            executor.shutdownNow();
        }
    }

    private Object approveOutcome(PickupController controller,
                                  PickupController.VerifyRequest request,
                                  String idempotencyKey,
                                  FirebaseUserDetails staff) {
        try {
            return controller.approve(request, idempotencyKey, null, staff);
        } catch (Exception exception) {
            return exception;
        }
    }
}
