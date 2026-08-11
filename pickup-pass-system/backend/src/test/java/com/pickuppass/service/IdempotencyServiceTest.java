package com.pickuppass.service;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.api.core.ApiFuture;
import com.pickuppass.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {

    @Test
    void fingerprintIsStableAndRequestSensitive() {
        IdempotencyService service = new IdempotencyService(mock(Firestore.class));
        String a1 = service.fingerprint("same-request");
        String a2 = service.fingerprint("same-request");
        String b = service.fingerprint("different-request");

        assertEquals(a1, a2);
        assertNotEquals(a1, b);
        assertEquals(64, a1.length());
    }

    @Test
    void missingIdempotencyKeyDoesNotTouchFirestore() throws Exception {
        Firestore firestore = mock(Firestore.class);
        IdempotencyService service = new IdempotencyService(firestore);

        Optional<String> result = service.findExisting("school1", "staff1", "pickup.approve", null, "fp");

        assertTrue(result.isEmpty());
        verifyNoInteractions(firestore);
    }

    @Test
    void existingKeyWithDifferentFingerprintIsRejected() throws Exception {
        Firestore firestore = mock(Firestore.class);
        CollectionReference collection = mock(CollectionReference.class);
        DocumentReference ref = mock(DocumentReference.class);
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
        @SuppressWarnings("unchecked") ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);

        when(firestore.collection("idempotencyKeys")).thenReturn(collection);
        when(collection.document(anyString())).thenReturn(ref);
        when(ref.get()).thenReturn(future);
        when(future.get()).thenReturn(snapshot);
        when(snapshot.exists()).thenReturn(true);
        when(snapshot.getString("requestFingerprint")).thenReturn("original-fingerprint");

        IdempotencyService service = new IdempotencyService(firestore);

        assertThrows(ConflictException.class, () -> service.findExisting(
                "school1", "staff1", "pickup.approve", "same-key", "changed-fingerprint"));
    }

    @Test
    void existingSuccessfulKeyReturnsOriginalExitLog() throws Exception {
        Firestore firestore = mock(Firestore.class);
        CollectionReference collection = mock(CollectionReference.class);
        DocumentReference ref = mock(DocumentReference.class);
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
        @SuppressWarnings("unchecked") ApiFuture<DocumentSnapshot> future = mock(ApiFuture.class);

        when(firestore.collection("idempotencyKeys")).thenReturn(collection);
        when(collection.document(anyString())).thenReturn(ref);
        when(ref.get()).thenReturn(future);
        when(future.get()).thenReturn(snapshot);
        when(snapshot.exists()).thenReturn(true);
        when(snapshot.getString("requestFingerprint")).thenReturn("fp");
        when(snapshot.getString("exitLogId")).thenReturn("exit-log-123");

        IdempotencyService service = new IdempotencyService(firestore);

        assertEquals(Optional.of("exit-log-123"), service.findExisting(
                "school1", "staff1", "pickup.approve", "same-key", "fp"));
    }
}
