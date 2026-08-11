package com.pickuppass.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.pickuppass.dto.QrVerificationResult;
import com.pickuppass.exception.ConflictException;
import com.pickuppass.exception.ForbiddenException;
import com.pickuppass.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class QrVerificationService {

    private final Firestore firestore;
    private final Algorithm hmacAlgorithm;
    private final ZoneId schoolTimeZone;
    private final int dismissalWindowMinutes;

    public QrVerificationService(Firestore firestore,
                                  @Value("${qr.signing.secret}") String secret,
                                  @Value("${app.school-time-zone:Asia/Manila}") String schoolTimeZone,
                                  @Value("${qr.dismissal-window-minutes:120}") int dismissalWindowMinutes) {
        this.firestore = firestore;
        this.hmacAlgorithm = Algorithm.HMAC256(secret);
        this.schoolTimeZone = ZoneId.of(schoolTimeZone);
        this.dismissalWindowMinutes = dismissalWindowMinutes;
    }

    public QrVerificationResult verify(String qrToken, String scanningSchoolId)
            throws ExecutionException, InterruptedException {
        if (qrToken == null || qrToken.isBlank()) {
            return QrVerificationResult.fail("QR token is required");
        }
        if (scanningSchoolId == null || scanningSchoolId.isBlank()) {
            return QrVerificationResult.fail("Staff account is not assigned to a school");
        }

        DecodedJWT decoded;
        try {
            decoded = JWT.require(hmacAlgorithm).withIssuer("pps").build().verify(qrToken);
        } catch (JWTVerificationException e) {
            return QrVerificationResult.fail("Invalid, tampered, or expired QR code");
        }

        String schoolId = decoded.getClaim("sid").asString();
        String studentId = decoded.getClaim("stid").asString();
        String parentUid = decoded.getClaim("pid").asString();
        String nonce = decoded.getClaim("n").asString();
        if (schoolId == null || studentId == null || parentUid == null || nonce == null) {
            return QrVerificationResult.fail("QR code is missing required data");
        }

        if (!schoolId.equals(scanningSchoolId)) {
            return QrVerificationResult.fail("QR code does not belong to this school");
        }

        String policyViolation = pickupPolicyViolation(scanningSchoolId);
        if (policyViolation != null) {
            return QrVerificationResult.fail(policyViolation);
        }

        DocumentReference tokenRef = firestore.collection("pickupTokens").document(nonce);
        DocumentSnapshot tokenSnap = tokenRef.get().get();
        if (!tokenSnap.exists()) return QrVerificationResult.fail("Unknown or revoked token");
        if (Boolean.TRUE.equals(tokenSnap.getBoolean("used"))) return QrVerificationResult.fail("QR code already used or superseded");
        if (!schoolId.equals(tokenSnap.getString("schoolId"))
                || !studentId.equals(tokenSnap.getString("studentId"))
                || !parentUid.equals(tokenSnap.getString("parentUid"))) {
            return QrVerificationResult.fail("QR token ledger mismatch");
        }

        Timestamp dismissalDeadline = tokenSnap.getTimestamp("dismissalDeadline");
        if (dismissalDeadline != null && dismissalDeadline.toDate().getTime() < System.currentTimeMillis()) {
            return QrVerificationResult.fail("Dismissal window has expired");
        }
        Timestamp issuedAt = tokenSnap.getTimestamp("issuedAt");
        if (dismissalDeadline == null && issuedAt != null) {
            long ageMillis = System.currentTimeMillis() - issuedAt.toDate().getTime();
            if (ageMillis > TimeUnit.MINUTES.toMillis(dismissalWindowMinutes)) {
                return QrVerificationResult.fail("Dismissal window has expired");
            }
        }

        DocumentSnapshot studentSnap = firestore.collection("students").document(studentId).get().get();
        if (!studentSnap.exists() || !scanningSchoolId.equals(studentSnap.getString("schoolId"))) {
            return QrVerificationResult.fail("Student not found in this school");
        }
        @SuppressWarnings("unchecked")
        List<String> guardians = (List<String>) studentSnap.get("guardianUids");
        if (guardians == null || !guardians.contains(parentUid)) {
            return QrVerificationResult.fail("Guardian is no longer authorized for this student");
        }

        return QrVerificationResult.success(studentId, parentUid, tokenRef);
    }

    /**
     * Atomically redeems the QR token, acquires today's per-student dismissal lock,
     * and creates the immutable exit log. Concurrent/retried approvals cannot release
     * the same student twice.
     */
    public String markUsedAndLog(QrVerificationResult result, String verifiedByUid, String schoolId)
            throws ExecutionException, InterruptedException {
        String businessDate = LocalDate.now(schoolTimeZone).toString();
        String lockId = safeId(schoolId) + "_" + businessDate + "_" + safeId(result.getStudentId());
        DocumentReference lockRef = firestore.collection("dismissalLocks").document(lockId);
        DocumentReference exitLogRef = firestore.collection("exitLogs").document();

        firestore.runTransaction(tx -> {
            DocumentSnapshot token = tx.get(result.getTokenRef()).get();
            if (!token.exists() || Boolean.TRUE.equals(token.getBoolean("used"))) {
                throw new ConflictException("QR code was already used or superseded");
            }
            DocumentSnapshot lock = tx.get(lockRef).get();
            if (lock.exists()) {
                throw new ConflictException("Student has already been dismissed today");
            }

            Map<String, Object> lockData = new HashMap<>();
            lockData.put("schoolId", schoolId);
            lockData.put("studentId", result.getStudentId());
            lockData.put("businessDate", businessDate);
            lockData.put("exitLogId", exitLogRef.getId());
            lockData.put("createdAt", FieldValue.serverTimestamp());

            Map<String, Object> log = buildExitLog(schoolId, result.getStudentId(), result.getParentUid(),
                    verifiedByUid, "qr_scan", businessDate, null);

            tx.update(result.getTokenRef(), "used", true, "usedAt", FieldValue.serverTimestamp());
            tx.set(lockRef, lockData);
            tx.set(exitLogRef, log);
            return null;
        }).get();
        return exitLogRef.getId();
    }

    /** Controlled fallback for dead phones, camera failures, or other documented exceptions. */
    public String manualOverride(String studentId, String guardianUid, String reason,
                                 String verifiedByUid, String schoolId)
            throws ExecutionException, InterruptedException {
        if (reason == null || reason.trim().length() < 5) {
            throw new IllegalArgumentException("A clear manual override reason is required");
        }
        if (!isManualOverrideAllowed(schoolId)) {
            throw new ForbiddenException("Manual pickup override is disabled by this school's pickup policy");
        }
        DocumentReference studentRef = firestore.collection("students").document(studentId);
        DocumentSnapshot student = studentRef.get().get();
        if (!student.exists() || !schoolId.equals(student.getString("schoolId"))) {
            throw new NotFoundException("Student not found in your school");
        }
        @SuppressWarnings("unchecked")
        List<String> guardians = (List<String>) student.get("guardianUids");
        if (guardians == null || !guardians.contains(guardianUid)) {
            throw new ForbiddenException("Selected guardian is not authorized for this student");
        }

        String businessDate = LocalDate.now(schoolTimeZone).toString();
        String lockId = safeId(schoolId) + "_" + businessDate + "_" + safeId(studentId);
        DocumentReference lockRef = firestore.collection("dismissalLocks").document(lockId);
        DocumentReference exitLogRef = firestore.collection("exitLogs").document();

        firestore.runTransaction(tx -> {
            DocumentSnapshot lock = tx.get(lockRef).get();
            if (lock.exists()) throw new ConflictException("Student has already been dismissed today");

            Map<String, Object> lockData = new HashMap<>();
            lockData.put("schoolId", schoolId);
            lockData.put("studentId", studentId);
            lockData.put("businessDate", businessDate);
            lockData.put("exitLogId", exitLogRef.getId());
            lockData.put("createdAt", FieldValue.serverTimestamp());

            Map<String, Object> log = buildExitLog(schoolId, studentId, guardianUid,
                    verifiedByUid, "manual_override", businessDate, reason.trim());
            tx.set(lockRef, lockData);
            tx.set(exitLogRef, log);
            return null;
        }).get();
        return exitLogRef.getId();
    }

    private Map<String, Object> buildExitLog(String schoolId, String studentId, String parentUid,
                                              String verifiedByUid, String method, String businessDate,
                                              String overrideReason) {
        Map<String, Object> log = new HashMap<>();
        log.put("schoolId", schoolId);
        log.put("studentId", studentId);
        log.put("parentUid", parentUid);
        log.put("verifiedByUid", verifiedByUid);
        log.put("timestamp", FieldValue.serverTimestamp());
        log.put("businessDate", businessDate);
        log.put("method", method);
        if (overrideReason != null) log.put("overrideReason", overrideReason);
        return log;
    }

    @SuppressWarnings("unchecked")
    private String pickupPolicyViolation(String schoolId) throws ExecutionException, InterruptedException {
        DocumentSnapshot school = firestore.collection("schools").document(schoolId).get().get();
        if (!school.exists()) return "School is not active or could not be found";

        Map<String, Object> policy = (Map<String, Object>) school.get("pickupPolicy");
        if (policy == null || !"time_window".equals(String.valueOf(policy.get("mode")))) {
            return null; // Original PickupPass behavior: any currently-valid QR can be scanned.
        }

        Object startObj = policy.get("earliestPickupTime");
        Object endObj = policy.get("latestPickupTime");
        if (startObj == null || endObj == null) return null; // fail open for legacy/malformed optional config

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
            LocalTime start = LocalTime.parse(String.valueOf(startObj), formatter);
            LocalTime end = LocalTime.parse(String.valueOf(endObj), formatter);
            LocalTime now = LocalTime.now(schoolTimeZone);
            if (now.isBefore(start) || now.isAfter(end)) {
                return "Pickup is allowed between " + start.format(formatter) + " and " + end.format(formatter);
            }
            return null;
        } catch (RuntimeException ignored) {
            return null; // administrators can repair malformed policy without blocking dismissal
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isManualOverrideAllowed(String schoolId) throws ExecutionException, InterruptedException {
        DocumentSnapshot school = firestore.collection("schools").document(schoolId).get().get();
        if (!school.exists()) return false;
        Map<String, Object> policy = (Map<String, Object>) school.get("pickupPolicy");
        return policy == null || !Boolean.FALSE.equals(policy.get("allowManualOverride"));
    }

    private String safeId(String value) {
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
