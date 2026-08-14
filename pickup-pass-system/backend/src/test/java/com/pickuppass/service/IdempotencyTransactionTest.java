package com.pickuppass.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Transaction;
import com.pickuppass.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyTransactionTest {

    private Firestore firestore;
    private DocumentReference reference;
    private Transaction transaction;
    private DocumentSnapshot existing;
    private IdempotencyService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        firestore = mock(Firestore.class);
        CollectionReference collection = mock(CollectionReference.class);
        reference = mock(DocumentReference.class);
        transaction = mock(Transaction.class);
        existing = mock(DocumentSnapshot.class);
        ApiFuture<DocumentSnapshot> readFuture = mock(ApiFuture.class);
        ApiFuture<Object> transactionFuture = mock(ApiFuture.class);

        when(firestore.collection("idempotencyKeys")).thenReturn(collection);
        when(collection.document(anyString())).thenReturn(reference);
        when(transaction.get(reference)).thenReturn(readFuture);
        when(readFuture.get()).thenReturn(existing);
        when(transactionFuture.get()).thenReturn(null);
        when(firestore.runTransaction(any())).thenAnswer(invocation -> {
            Transaction.Function<Object> callback = invocation.getArgument(0);
            callback.updateCallback(transaction);
            return transactionFuture;
        });
        service = new IdempotencyService(firestore);
    }

    @Test
    @SuppressWarnings("unchecked")
    void firstWriterStoresReplayResultInsideTransaction() throws Exception {
        when(existing.exists()).thenReturn(false);

        service.storeResult("school-1", "staff-1", "pickup.approve", "request-1", "fingerprint-1", "exit-log-1");

        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(transaction).set(eq(reference), data.capture());
        assertEquals("school-1", data.getValue().get("schoolId"));
        assertEquals("staff-1", data.getValue().get("actorUid"));
        assertEquals("pickup.approve", data.getValue().get("operation"));
        assertEquals("fingerprint-1", data.getValue().get("requestFingerprint"));
        assertEquals("exit-log-1", data.getValue().get("exitLogId"));
        assertTrue(data.getValue().containsKey("createdAt"));
        assertTrue(data.getValue().containsKey("expiresAt"));
    }

    @Test
    void identicalRetryDoesNotOverwriteWinningResult() throws Exception {
        when(existing.exists()).thenReturn(true);
        when(existing.getString("requestFingerprint")).thenReturn("fingerprint-1");

        service.storeResult("school-1", "staff-1", "pickup.approve", "request-1", "fingerprint-1", "later-log");

        verify(transaction, never()).set(any(DocumentReference.class), any(Map.class));
    }

    @Test
    void sameKeyForDifferentRequestIsRejectedInsideTransaction() {
        when(existing.exists()).thenReturn(true);
        when(existing.getString("requestFingerprint")).thenReturn("original-fingerprint");

        assertThrows(ConflictException.class, () -> service.storeResult(
                "school-1", "staff-1", "pickup.approve", "request-1", "changed-fingerprint", "exit-log-2"));
        verify(transaction, never()).set(any(DocumentReference.class), any(Map.class));
    }
}
