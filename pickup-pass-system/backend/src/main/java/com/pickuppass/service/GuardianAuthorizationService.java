package com.pickuppass.service;

import com.google.cloud.firestore.DocumentSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** Central source of truth for permanent and one-day guardian authorization. */
@Service
public class GuardianAuthorizationService {

    private final ZoneId schoolTimeZone;

    public GuardianAuthorizationService(@Value("${app.school-time-zone:Asia/Manila}") String schoolTimeZone) {
        this.schoolTimeZone = ZoneId.of(schoolTimeZone);
    }

    @SuppressWarnings("unchecked")
    public AuthorizationDecision check(DocumentSnapshot student, String guardianUid) {
        if (student == null || !student.exists() || guardianUid == null || guardianUid.isBlank()) {
            return AuthorizationDecision.denied("Guardian is not authorized for this student");
        }

        List<String> guardianUids = (List<String>) student.get("guardianUids");
        if (guardianUids == null || !guardianUids.contains(guardianUid)) {
            return AuthorizationDecision.denied("Guardian is no longer authorized for this student");
        }

        Map<String, Object> guardians = (Map<String, Object>) student.get("guardians");
        if (guardians == null) {
            return AuthorizationDecision.allowed(false); // legacy record
        }

        Object rawEntry = guardians.get(guardianUid);
        if (!(rawEntry instanceof Map<?, ?> rawMap)) {
            return AuthorizationDecision.allowed(false); // legacy guardianUids-only record
        }

        Map<String, Object> entry = (Map<String, Object>) rawMap;
        String type = stringValue(entry.get("authorizationType"), "permanent");
        if (!"temporary".equalsIgnoreCase(type)) {
            return AuthorizationDecision.allowed(false);
        }

        String validDate = stringValue(entry.get("validDate"), "");
        if (validDate.isBlank()) {
            return AuthorizationDecision.denied("Temporary guardian authorization is missing its pickup date");
        }

        LocalDate today = LocalDate.now(schoolTimeZone);
        LocalDate authorizedDate;
        try {
            authorizedDate = LocalDate.parse(validDate);
        } catch (RuntimeException e) {
            return AuthorizationDecision.denied("Temporary guardian authorization is invalid");
        }

        if (authorizedDate.isBefore(today)) {
            return AuthorizationDecision.denied("Temporary guardian authorization has expired");
        }
        if (authorizedDate.isAfter(today)) {
            return AuthorizationDecision.denied("Temporary guardian authorization is valid on " + authorizedDate);
        }

        long remainingUses = numberValue(entry.get("remainingUses"), 1L);
        if (remainingUses <= 0) {
            return AuthorizationDecision.denied("Temporary guardian authorization has already been used");
        }

        return AuthorizationDecision.allowed(true);
    }

    public boolean isTemporary(DocumentSnapshot student, String guardianUid) {
        return check(student, guardianUid).temporary();
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static long numberValue(Object value, long fallback) {
        return value instanceof Number n ? n.longValue() : fallback;
    }

    public record AuthorizationDecision(boolean allowed, boolean temporary, String reason) {
        public static AuthorizationDecision allowed(boolean temporary) {
            return new AuthorizationDecision(true, temporary, null);
        }

        public static AuthorizationDecision denied(String reason) {
            return new AuthorizationDecision(false, false, reason);
        }
    }
}
