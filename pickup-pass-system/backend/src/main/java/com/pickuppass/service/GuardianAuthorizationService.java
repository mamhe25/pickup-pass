package com.pickuppass.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** Central source of truth for permanent/temporary guardian authorization and verification status. */
@Service
public class GuardianAuthorizationService {

    private final Firestore firestore;
    private final ZoneId schoolTimeZone;

    @Autowired
    public GuardianAuthorizationService(
            Firestore firestore,
            @Value("${app.school-time-zone:Asia/Manila}") String schoolTimeZone) {
        this.firestore = firestore;
        this.schoolTimeZone = ZoneId.of(schoolTimeZone);
    }

    /** Test/backward-compatible constructor used by existing unit tests. */
    public GuardianAuthorizationService(String schoolTimeZone) {
        this.firestore = null;
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

        AuthorizationDecision verificationDecision = checkVerification(student.getString("schoolId"), guardianUid);
        if (!verificationDecision.allowed()) return verificationDecision;

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
            return checkPermanentSchedule(entry);
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

    @SuppressWarnings("unchecked")
    private AuthorizationDecision checkPermanentSchedule(Map<String, Object> entry) {
        if (!Boolean.TRUE.equals(entry.get("pickupScheduleEnabled"))) {
            return AuthorizationDecision.allowed(false);
        }

        LocalDate today = LocalDate.now(schoolTimeZone);
        String start = stringValue(entry.get("scheduleStartDate"), "").trim();
        String end = stringValue(entry.get("scheduleEndDate"), "").trim();
        try {
            if (!start.isBlank() && today.isBefore(LocalDate.parse(start))) {
                return AuthorizationDecision.denied("Guardian pickup schedule starts on " + start);
            }
            if (!end.isBlank() && today.isAfter(LocalDate.parse(end))) {
                return AuthorizationDecision.denied("Guardian pickup schedule ended on " + end);
            }
        } catch (RuntimeException ex) {
            return AuthorizationDecision.denied("Guardian pickup schedule is invalid");
        }

        Object rawDays = entry.get("pickupDays");
        if (!(rawDays instanceof List<?> days) || days.isEmpty()) {
            return AuthorizationDecision.denied("Guardian pickup schedule has no authorized days");
        }

        String todayName = today.getDayOfWeek().name();
        boolean allowedToday = days.stream().map(String::valueOf).anyMatch(todayName::equalsIgnoreCase);
        if (!allowedToday) {
            String friendly = today.getDayOfWeek().name().toLowerCase();
            friendly = Character.toUpperCase(friendly.charAt(0)) + friendly.substring(1);
            return AuthorizationDecision.denied("Guardian pickup is not authorized on " + friendly);
        }
        return AuthorizationDecision.allowed(false);
    }

    private AuthorizationDecision checkVerification(String schoolId, String guardianUid) {
        if (firestore == null) return AuthorizationDecision.allowed(false);
        try {
            DocumentSnapshot user = firestore.collection("users").document(guardianUid).get().get();
            if (!user.exists()) return AuthorizationDecision.denied("Guardian account no longer exists");
            if (schoolId != null && !schoolId.equals(user.getString("schoolId"))) {
                return AuthorizationDecision.denied("Guardian account does not belong to this school");
            }
            if (Boolean.FALSE.equals(user.getBoolean("isActive"))) {
                return AuthorizationDecision.denied("Guardian account is inactive");
            }

            String status = stringValue(user.get("guardianVerificationStatus"), "verified").toLowerCase();
            if ("suspended".equals(status)) {
                return AuthorizationDecision.denied("Guardian pickup access has been suspended by the school");
            }

            boolean required = false;
            if (schoolId != null && !schoolId.isBlank()) {
                DocumentSnapshot school = firestore.collection("schools").document(schoolId).get().get();
                required = school.exists() && Boolean.TRUE.equals(school.getBoolean("guardianVerificationRequired"));
            }
            if (required && !"verified".equals(status)) {
                return AuthorizationDecision.denied("Guardian identity must be verified by the school before pickup");
            }
            return AuthorizationDecision.allowed(false);
        } catch (Exception e) {
            // Verification is safety-sensitive; fail closed when its source of truth is unavailable.
            return AuthorizationDecision.denied("Unable to verify guardian pickup authorization right now");
        }
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
