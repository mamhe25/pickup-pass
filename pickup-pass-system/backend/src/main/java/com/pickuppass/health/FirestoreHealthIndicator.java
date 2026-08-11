package com.pickuppass.health;

import com.google.cloud.firestore.Firestore;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** Verifies that the backend can actually reach Firestore, not merely that the JVM is alive. */
@Component
public class FirestoreHealthIndicator implements HealthIndicator {

    private final Firestore firestore;

    public FirestoreHealthIndicator(Firestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public Health health() {
        try {
            firestore.collection("schools").limit(1).get().get(3, TimeUnit.SECONDS);
            return Health.up().build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
