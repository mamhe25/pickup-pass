package com.pickuppass.controller;

import com.pickuppass.dto.QrVerificationResult;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.IdempotencyService;
import com.pickuppass.service.PickupMetricsService;
import com.pickuppass.service.PushNotificationService;
import com.pickuppass.service.QrVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PickupControllerTest {

    @Test
    void approveRejectsInvalidQrWithoutWritingRelease() throws Exception {
        QrVerificationService qr = mock(QrVerificationService.class);
        PushNotificationService push = mock(PushNotificationService.class);
        AuditService audit = mock(AuditService.class);
        PickupMetricsService metrics = mock(PickupMetricsService.class);
        IdempotencyService idempotency = mock(IdempotencyService.class);
        PickupController controller = new PickupController(qr, push, audit, metrics, idempotency);
        FirebaseUserDetails staff = new FirebaseUserDetails("staff1", "staff@test.com", "school1", "teacher");
        PickupController.VerifyRequest req = new PickupController.VerifyRequest();
        req.setQrToken("bad-token");

        when(idempotency.fingerprint("bad-token")).thenReturn("fp-bad");
        when(idempotency.findExisting("school1", "staff1", "pickup.approve", "request-1", "fp-bad"))
                .thenReturn(Optional.empty());
        when(qr.verify("bad-token", "school1")).thenReturn(QrVerificationResult.fail("Invalid QR"));

        ResponseEntity<?> response = controller.approve(req, "request-1", staff);

        assertEquals(400, response.getStatusCode().value());
        verify(qr, never()).markUsedAndLog(any(), anyString(), anyString());
        verify(idempotency, never()).storeResult(anyString(), anyString(), anyString(), any(), anyString(), anyString());
        verifyNoInteractions(push, audit);
    }

    @Test
    void approveCreatesReleaseThenStoresReplayResultNotifiesAndAudits() throws Exception {
        QrVerificationService qr = mock(QrVerificationService.class);
        PushNotificationService push = mock(PushNotificationService.class);
        AuditService audit = mock(AuditService.class);
        PickupMetricsService metrics = mock(PickupMetricsService.class);
        IdempotencyService idempotency = mock(IdempotencyService.class);
        PickupController controller = new PickupController(qr, push, audit, metrics, idempotency);
        FirebaseUserDetails staff = new FirebaseUserDetails("staff1", "staff@test.com", "school1", "teacher");
        PickupController.VerifyRequest req = new PickupController.VerifyRequest();
        req.setQrToken("good-token");
        QrVerificationResult result = QrVerificationResult.success("student1", "guardian1", null);

        when(idempotency.fingerprint("good-token")).thenReturn("fp-good");
        when(idempotency.findExisting("school1", "staff1", "pickup.approve", "request-2", "fp-good"))
                .thenReturn(Optional.empty());
        when(qr.verify("good-token", "school1")).thenReturn(result);
        when(qr.markUsedAndLog(result, "staff1", "school1")).thenReturn("log1");

        ResponseEntity<?> response = controller.approve(req, "request-2", staff);

        assertEquals(200, response.getStatusCode().value());
        verify(idempotency).storeResult("school1", "staff1", "pickup.approve", "request-2", "fp-good", "log1");
        verify(push).notifyGuardiansOfPickup("student1", "guardian1");
        verify(audit).record(eq(staff), eq("pickup.approved"), eq("exitLog"), eq("log1"), anyMap());
    }

    @Test
    void approveReturnsStoredResultForSafeNetworkRetry() throws Exception {
        QrVerificationService qr = mock(QrVerificationService.class);
        PushNotificationService push = mock(PushNotificationService.class);
        AuditService audit = mock(AuditService.class);
        PickupMetricsService metrics = mock(PickupMetricsService.class);
        IdempotencyService idempotency = mock(IdempotencyService.class);
        PickupController controller = new PickupController(qr, push, audit, metrics, idempotency);
        FirebaseUserDetails staff = new FirebaseUserDetails("staff1", "staff@test.com", "school1", "teacher");
        PickupController.VerifyRequest req = new PickupController.VerifyRequest();
        req.setQrToken("good-token");

        when(idempotency.fingerprint("good-token")).thenReturn("fp-good");
        when(idempotency.findExisting("school1", "staff1", "pickup.approve", "request-3", "fp-good"))
                .thenReturn(Optional.of("existing-log"));

        ResponseEntity<?> response = controller.approve(req, "request-3", staff);

        assertEquals(200, response.getStatusCode().value());
        verifyNoInteractions(qr, push, audit);
        verify(idempotency, never()).storeResult(anyString(), anyString(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void manualOverrideUsesControlledAdminFlowAndStoresIdempotency() throws Exception {
        QrVerificationService qr = mock(QrVerificationService.class);
        PushNotificationService push = mock(PushNotificationService.class);
        AuditService audit = mock(AuditService.class);
        PickupMetricsService metrics = mock(PickupMetricsService.class);
        IdempotencyService idempotency = mock(IdempotencyService.class);
        PickupController controller = new PickupController(qr, push, audit, metrics, idempotency);
        FirebaseUserDetails admin = new FirebaseUserDetails("admin1", "admin@test.com", "school1", "school_admin");
        PickupController.ManualOverrideRequest req = new PickupController.ManualOverrideRequest();
        req.setStudentId("student1");
        req.setGuardianUid("guardian1");
        req.setReason("Parent phone battery is dead");

        String source = "student1\nguardian1\nParent phone battery is dead";
        when(idempotency.fingerprint(source)).thenReturn("fp-manual");
        when(idempotency.findExisting("school1", "admin1", "pickup.manual_override", "manual-1", "fp-manual"))
                .thenReturn(Optional.empty());
        when(qr.manualOverride("student1", "guardian1", "Parent phone battery is dead", "admin1", "school1"))
                .thenReturn("log2");

        ResponseEntity<?> response = controller.manualOverride(req, "manual-1", admin);

        assertEquals(200, response.getStatusCode().value());
        verify(idempotency).storeResult("school1", "admin1", "pickup.manual_override", "manual-1", "fp-manual", "log2");
        verify(push).notifyGuardiansOfPickup("student1", "guardian1");
        verify(audit).record(eq(admin), eq("pickup.manual_override"), eq("exitLog"), eq("log2"), anyMap());
    }
}
