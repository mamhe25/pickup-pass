package com.pickuppass.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.pickuppass.exception.ForbiddenException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class QrTokenIssuanceServiceTest {

    private static final String SECRET =
            "test-only-signing-secret-with-sufficient-length";

    @Test
    void qrIssuanceFailsUntilGuardianPhotoIsServerValidated() throws Exception {
        Firestore firestore = mock(Firestore.class);
        GuardianAuthorizationService guardianAuthorization =
                mock(GuardianAuthorizationService.class);
        LaunchModeService launchMode = mock(LaunchModeService.class);

        DocumentSnapshot student = mock(DocumentSnapshot.class);
        when(student.exists()).thenReturn(true);
        when(student.getString("schoolId"))
                .thenReturn("school-1");
        when(student.getString("status"))
                .thenReturn("active");
        stubDocument(
                firestore,
                "students",
                "student-1",
                student
        );

        when(guardianAuthorization.check(
                student,
                "guardian-1"
        )).thenReturn(
                GuardianAuthorizationService
                        .AuthorizationDecision
                        .allowed(false)
        );

        DocumentSnapshot guardian = mock(DocumentSnapshot.class);
        when(guardian.exists()).thenReturn(true);
        when(guardian.getString("schoolId"))
                .thenReturn("school-1");
        when(guardian.getBoolean("isActive"))
                .thenReturn(true);
        when(guardian.getString("photoUrl"))
                .thenReturn("data:image/jpeg;base64,abc");
        when(guardian.getString("photoValidationStatus"))
                .thenReturn("missing");
        stubDocument(
                firestore,
                "users",
                "guardian-1",
                guardian
        );

        QrTokenIssuanceService service =
                new QrTokenIssuanceService(
                        firestore,
                        SECRET,
                        15,
                        120,
                        guardianAuthorization,
                        launchMode
                );

        ForbiddenException error =
                assertThrows(
                        ForbiddenException.class,
                        () -> service.issueToken(
                                "guardian-1",
                                "school-1",
                                "student-1"
                        )
                );

        assertTrue(
                error.getMessage()
                        .contains("valid verification photo")
        );
        verifyNoInteractions(launchMode);
    }

    @SuppressWarnings("unchecked")
    private void stubDocument(
            Firestore firestore,
            String collectionName,
            String documentId,
            DocumentSnapshot snapshot) throws Exception {

        CollectionReference collection =
                mock(CollectionReference.class);
        DocumentReference document =
                mock(DocumentReference.class);
        ApiFuture<DocumentSnapshot> future =
                mock(ApiFuture.class);

        when(firestore.collection(collectionName))
                .thenReturn(collection);
        when(collection.document(documentId))
                .thenReturn(document);
        when(document.get())
                .thenReturn(future);
        when(future.get())
                .thenReturn(snapshot);
    }
}
