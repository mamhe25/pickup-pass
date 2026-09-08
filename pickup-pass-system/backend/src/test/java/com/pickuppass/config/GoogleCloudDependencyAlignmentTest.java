package com.pickuppass.config;

import com.google.cloud.firestore.FirestoreOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Guards the Google client-library runtime linkage used during Firebase
 * Firestore initialization. A mismatched GAX/Google Auth dependency graph can
 * compile successfully but fail only when FirestoreOptions builds its channel
 * provider, which previously caused Cloud Run to crash before binding PORT.
 */
class GoogleCloudDependencyAlignmentTest {

    @Test
    void firestoreOptionsCanBuildWithResolvedGoogleRuntimeDependencies() {
        assertDoesNotThrow(() ->
                FirestoreOptions.newBuilder()
                        .setProjectId("pickup-pass-test")
                        .build()
        );
    }
}
