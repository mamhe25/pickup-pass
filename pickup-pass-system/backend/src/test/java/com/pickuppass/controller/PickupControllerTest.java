package com.pickuppass.controller;

import com.pickuppass.dto.QrVerificationResult;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.PushNotificationService;
import com.pickuppass.service.QrVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PickupControllerTest {

    @Test
    void approveRejectsInvalidQrWithoutWritingRelease() throws Exception {
        QrVerificationService qr = mock(QrVerificationService.class);
        PushNotificationService push = mock(PushNotificationService.class);
        AuditService audit = mock(AuditService.class);
        PickupController controller = new PickupController(qr, push, audit);
        FirebaseUserDetails staff = new FirebaseUserDetails("staff1", "staff@test.com", "school1", "teacher");
        PickupController.VerifyRequest req = new PickupController.VerifyRequest();
        req.setQrToken("bad-token");
        when(qr.verify("bad-token", "school1")).thenReturn(QrVerificationResult.fail("Invalid QR"));

        ResponseEntity<?> response = controller.approve(req, staff);

        assertEquals(400, response.getStatusCode().value());
        verify(qr, never()).markUsedAndLog(any(), anyString(), anyString());
        verifyNoInteractions(push, audit);
    }

    @Test
    void approveCreatesReleaseThenNotifiesAndAudits() throws Exception {
        QrVerificationService qr = mock(QrVerificationService.class);
        PushNotificationService push = mock(PushNotificationService.class);
        AuditService audit = mock(AuditService.class);
        PickupController controller = new PickupController(qr, push, audit);
        FirebaseUserDetails staff = new FirebaseUserDetails("staff1", "staff@test.com", "school1", "teacher");
        PickupController.VerifyRequest req = new PickupController.VerifyRequest();
        req.setQrToken("good-token");
        QrVerificationResult result = QrVerificationResult.success("student1", "guardian1", null);
        when(qr.verify("good-token", "school1")).thenReturn(result);
        when(qr.markUsedAndLog(result, "staff1", "school1")).thenReturn("log1");

        ResponseEntity<?> response = controller.approve(req, staff);

        assertEquals(200, response.getStatusCode().value());
        verify(push).notifyGuardiansOfPickup("student1", "guardian1");
        verify(audit).record(eq(staff), eq("pickup.approved"), eq("exitLog"), eq("log1"), anyMap());
    }

    @Test
    void manualOverrideUsesControlledAdminFlow() throws Exception {
        QrVerificationService qr = mock(QrVerificationService.class);
        PushNotificationService push = mock(PushNotificationService.class);
        AuditService audit = mock(AuditService.class);
        PickupController controller = new PickupController(qr, push, audit);
        FirebaseUserDetails admin = new FirebaseUserDetails("admin1", "admin@test.com", "school1", "school_admin");
        PickupController.ManualOverrideRequest req = new PickupController.ManualOverrideRequest();
        req.setStudentId("student1");
        req.setGuardianUid("guardian1");
        req.setReason("Parent phone battery is dead");
        when(qr.manualOverride("student1", "guardian1", "Parent phone battery is dead", "admin1", "school1"))
                .thenReturn("log2");

        ResponseEntity<?> response = controller.manualOverride(req, admin);

        assertEquals(200, response.getStatusCode().value());
        verify(push).notifyGuardiansOfPickup("student1", "guardian1");
        verify(audit).record(eq(admin), eq("pickup.manual_override"), eq("exitLog"), eq("log2"), anyMap());
    }
}
