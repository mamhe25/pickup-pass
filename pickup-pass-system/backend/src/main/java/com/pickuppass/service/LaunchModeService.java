package com.pickuppass.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.pickuppass.exception.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

/**
 * Resolves whether a school's dismissal workflow is running in pre-launch
 * test mode or approved production mode.
 *
 * School account status and subscription access remain separate concerns.
 * Only an explicit launchStatus=approved switches dismissal records to
 * production mode.
 */
@Service
public class LaunchModeService {

    public static final String PRELAUNCH_TEST = "prelaunch_test";
    public static final String PRODUCTION = "production";

    private final Firestore firestore;

    public LaunchModeService(Firestore firestore) {
        this.firestore = firestore;
    }

    public LaunchMode resolve(String schoolId)
            throws ExecutionException, InterruptedException {
        DocumentSnapshot school =
                firestore.collection("schools")
                        .document(schoolId)
                        .get()
                        .get();

        if (!school.exists()) {
            throw new NotFoundException("School not found");
        }

        String launchStatus = normalizeLaunchStatus(
                school.getString("launchStatus")
        );
        boolean testMode = !"approved".equals(launchStatus);

        return new LaunchMode(
                testMode,
                testMode ? PRELAUNCH_TEST : PRODUCTION,
                launchStatus
        );
    }

    private String normalizeLaunchStatus(String value) {
        if (value == null || value.isBlank()) {
            return "draft";
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case "approved", "review_requested" -> normalized;
            default -> "draft";
        };
    }

    public record LaunchMode(
            boolean testMode,
            String operationalMode,
            String launchStatus
    ) { }
}
