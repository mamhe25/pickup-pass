package com.pickuppass.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.pickuppass.dto.QrVerificationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QrVerificationServiceTest {

    private static final String SECRET = "test-only-signing-secret-with-sufficient-length";

    @Test
    void rejectsTamperedTokenBeforeReadingFirestore() throws Exception {
        Firestore firestore = mock(Firestore.class);
        QrVerificationService service = service(firestore);

        QrVerificationResult result = service.verify(signedToken("school-1", "student-1", "guardian-1", "nonce-1") + "tampered", "school-1");

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("Invalid or tampered PickupPass QR code"));
        verifyNoInteractions(firestore);
    }

    @Test
    void rejectsQrFromAnotherSchoolBeforeReadingFirestore() throws Exception {
        Firestore firestore = mock(Firestore.class);
        QrVerificationService service = service(firestore);

        QrVerificationResult result = service.verify(
                signedToken("school-1", "student-1", "guardian-1", "nonce-1"),
                "school-2");

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("does not belong to this school"));
        verifyNoInteractions(firestore);
    }

    @Test
    void rejectsTokenAlreadyConsumedOrSuperseded() throws Exception {
        Firestore firestore = mock(Firestore.class);
        stubDocument(firestore, "schools", "school-1", schoolDocument());

        DocumentSnapshot token = mock(DocumentSnapshot.class);
        when(token.exists()).thenReturn(true);
        when(token.getBoolean("used")).thenReturn(true);
        stubDocument(firestore, "pickupTokens", "nonce-1", token);

        QrVerificationResult result = service(firestore).verify(
                signedToken("school-1", "student-1", "guardian-1", "nonce-1"),
                "school-1");

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("already used or superseded"));
    }

    @Test
    void rejectsSignedTokenWhenServerLedgerBelongsToAnotherStudent() throws Exception {
        Firestore firestore = mock(Firestore.class);
        stubDocument(firestore, "schools", "school-1", schoolDocument());

        DocumentSnapshot token = mock(DocumentSnapshot.class);
        when(token.exists()).thenReturn(true);
        when(token.getBoolean("used")).thenReturn(false);
        when(token.getString("schoolId")).thenReturn("school-1");
        when(token.getString("studentId")).thenReturn("different-student");
        when(token.getString("parentUid")).thenReturn("guardian-1");
        stubDocument(firestore, "pickupTokens", "nonce-1", token);

        QrVerificationResult result = service(firestore).verify(
                signedToken("school-1", "student-1", "guardian-1", "nonce-1"),
                "school-1");

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("ledger mismatch"));
    }

    private QrVerificationService service(Firestore firestore) {
        return new QrVerificationService(
                firestore,
                SECRET,
                "Asia/Manila",
                120,
                mock(GuardianAuthorizationService.class));
    }

    private DocumentSnapshot schoolDocument() {
        DocumentSnapshot school = mock(DocumentSnapshot.class);
        when(school.exists()).thenReturn(true);
        when(school.get("pickupPolicy")).thenReturn(null);
        return school;
    }

    @SuppressWarnings("unchecked")
    private void stubDocument(Firestore firestore, String collectionName, String documentId, DocumentSnapshot snapshot)
            throws Exception {
        CollectionReference collection = mock(CollectionReference.class);
        DocumentReference document = mock(DocumentReference.class);
        ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);
        when(firestore.collection(collectionName)).thenReturn(collection);
        when(collection.document(documentId)).thenReturn(document);
        when(document.get()).thenReturn(future);
        when(future.get()).thenReturn(snapshot);
    }

    private String signedToken(String schoolId, String studentId, String guardianUid, String nonce) {
        return JWT.create()
                .withIssuer("pps")
                .withClaim("sid", schoolId)
                .withClaim("stid", studentId)
                .withClaim("pid", guardianUid)
                .withClaim("n", nonce)
                .withExpiresAt(Date.from(Instant.now().plusSeconds(300)))
                .sign(Algorithm.HMAC256(SECRET));
    }
}
