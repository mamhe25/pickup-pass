package com.pickuppass.controller;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.GuardianPhotoValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

class ParentProfileControllerTest {

    @Test
    void rejectedPhotoDoesNotReplaceExistingGuardianPhoto() throws Exception {
        Firestore firestore = mock(Firestore.class);
        GuardianPhotoValidationService validator =
                mock(GuardianPhotoValidationService.class);
        AuditService audit = mock(AuditService.class);

        DocumentReference userRef =
                stubGuardianProfile(firestore, "guardian-1", "school-1");

        when(validator.validate(any()))
                .thenReturn(
                        GuardianPhotoValidationService.ValidationResult.rejected(
                                "No clear human face was detected"
                        )
                );

        ParentProfileController controller =
                new ParentProfileController(
                        firestore,
                        validator,
                        audit
                );

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "guardian.jpg",
                        "image/jpeg",
                        new byte[] {1, 2, 3, 4}
                );

        FirebaseUserDetails parent =
                new FirebaseUserDetails(
                        "guardian-1",
                        "guardian@test.com",
                        "school-1",
                        "parent"
                );

        ResponseEntity<?> response =
                controller.uploadVerificationPhoto(
                        file,
                        parent
                );

        assertEquals(
                422,
                response.getStatusCode().value()
        );
        verify(userRef, never()).update(anyMap());
        verifyNoInteractions(audit);
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptedPhotoWritesServerControlledValidationState() throws Exception {
        Firestore firestore = mock(Firestore.class);
        GuardianPhotoValidationService validator =
                mock(GuardianPhotoValidationService.class);
        AuditService audit = mock(AuditService.class);

        DocumentReference userRef =
                stubGuardianProfile(firestore, "guardian-1", "school-1");
        ApiFuture<WriteResult> writeFuture = mock(ApiFuture.class);
        when(userRef.update(anyMap())).thenReturn(writeFuture);
        when(writeFuture.get()).thenReturn(mock(WriteResult.class));

        when(validator.validate(any()))
                .thenReturn(
                        GuardianPhotoValidationService.ValidationResult.accepted(
                                0.96f,
                                0.31
                        )
                );

        ParentProfileController controller =
                new ParentProfileController(
                        firestore,
                        validator,
                        audit
                );

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "guardian.jpg",
                        "image/jpeg",
                        new byte[] {1, 2, 3, 4}
                );

        FirebaseUserDetails parent =
                new FirebaseUserDetails(
                        "guardian-1",
                        "guardian@test.com",
                        "school-1",
                        "parent"
                );

        ResponseEntity<?> response =
                controller.uploadVerificationPhoto(
                        file,
                        parent
                );

        assertEquals(
                200,
                response.getStatusCode().value()
        );

        var captor =
                org.mockito.ArgumentCaptor
                        .forClass(Map.class);
        verify(userRef).update(captor.capture());

        Map<String, Object> update =
                (Map<String, Object>) captor.getValue();
        assertEquals(
                "verified",
                update.get("photoValidationStatus")
        );
        assertEquals(
                "google_cloud_vision",
                update.get("photoValidationProvider")
        );
        assertEquals(
                1,
                update.get("photoValidationVersion")
        );
        assertTrue(
                String.valueOf(update.get("photoUrl"))
                        .startsWith(
                                "data:image/jpeg;base64,"
                        )
        );

        verify(audit).record(
                eq(parent),
                eq("guardian.verification_photo_updated"),
                eq("user"),
                eq("guardian-1"),
                anyMap()
        );
    }

    @SuppressWarnings("unchecked")
    private DocumentReference stubGuardianProfile(
            Firestore firestore,
            String uid,
            String schoolId) throws Exception {

        CollectionReference users =
                mock(CollectionReference.class);
        DocumentReference userRef =
                mock(DocumentReference.class);
        ApiFuture<DocumentSnapshot> future =
                mock(ApiFuture.class);
        DocumentSnapshot user =
                mock(DocumentSnapshot.class);

        when(firestore.collection("users"))
                .thenReturn(users);
        when(users.document(uid))
                .thenReturn(userRef);
        when(userRef.get())
                .thenReturn(future);
        when(future.get())
                .thenReturn(user);

        when(user.exists()).thenReturn(true);
        when(user.getString("schoolId"))
                .thenReturn(schoolId);
        when(user.getString("role"))
                .thenReturn("parent");
        when(user.getBoolean("isActive"))
                .thenReturn(true);

        return userRef;
    }
}
