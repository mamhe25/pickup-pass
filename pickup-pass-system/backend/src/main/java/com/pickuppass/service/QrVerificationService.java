package com.pickuppass.service;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class QrVerificationService {
    private final Firestore firestore;
    private final Algorithm hmacAlgorithm;
    private final ZoneId schoolTimeZone;
    private final int dismissalWindowMinutes;
    private final GuardianAuthorizationService guardianAuthorizationService;
    public QrVerificationService(Firestore firestore,
                                  @Value("${qr.signing.secret}") String secret,
                                  @Value("${app.school-time-zone:Asia/Manila}") String schoolTimeZone,
                                  @Value("${qr.dismissal-window-minutes:120}") int dismissalWindowMinutes,
                                  GuardianAuthorizationService guardianAuthorizationService) {
        this.firestore = firestore;
        this.hmacAlgorithm = Algorithm.HMAC256(secret);
        this.schoolTimeZone = ZoneId.of(schoolTimeZone);
        this.dismissalWindowMinutes = dismissalWindowMinutes;
        this.guardianAuthorizationService = guardianAuthorizationService;
    }
    public QrVerificationResult verify(String qrToken, String scanningSchoolId)
            throws ExecutionException, InterruptedException {
        if (qrToken == null || qrToken.isBlank()) {
            return QrVerificationResult.fail("QR token is required");
        }
        if (scanningSchoolId == null || scanningSchoolId.isBlank()) {
            return QrVerificationResult.fail("Staff account is not assigned to a school");
        }
        if (!looksLikeCompactJwt(qrToken)) {
            return QrVerificationResult.fail("Invalid QR code - not a PickupPass QR code");
        }

        DecodedJWT decoded;
        try {
            decoded = JWT.require(hmacAlgorithm).withIssuer("pps").build().verify(qrToken);
        } catch (TokenExpiredException e) {
            return QrVerificationResult.fail("Pickup pass expired");
        } catch (JWTVerificationException e) {
            return QrVerificationResult.fail("Invalid or tampered PickupPass QR code");
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
        String studentStatus = studentSnap.getString("status");
        if (studentStatus != null && !studentStatus.isBlank() && !"active".equalsIgnoreCase(studentStatus)) {
            return QrVerificationResult.fail("Student pickup access is not active");
        }
        if (hasDismissalLock(scanningSchoolId, studentId)) {
            return QrVerificationResult.fail("Student has already been dismissed today");
        }
        GuardianAuthorizationService.AuthorizationDecision guardianDecision =
                guardianAuthorizationService.check(studentSnap, parentUid);
        if (!guardianDecision.allowed()) {
            return QrVerificationResult.fail(guardianDecision.reason());
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
        return markUsedAndLog(result, verifiedByUid, schoolId, null);
    }
    public String markUsedAndLog(QrVerificationResult result, String verifiedByUid, String schoolId, String pickupGateId)
            throws ExecutionException, InterruptedException {
        String businessDate = LocalDate.now(schoolTimeZone).toString();
        String lockId = safeId(schoolId) + "_" + businessDate + "_" + safeId(result.getStudentId());
        DocumentReference lockRef = firestore.collection("dismissalLocks").document(lockId);
        DocumentReference exitLogRef = firestore.collection("exitLogs").document();
        DocumentReference studentRef = firestore.collection("students").document(result.getStudentId());
        ExitSnapshot snapshot = loadExitSnapshot(result.getStudentId(), result.getParentUid(), verifiedByUid, schoolId);
        PickupGateSnapshot gateSnapshot = resolvePickupGate(schoolId, pickupGateId, true, verifiedByUid);

        TransactionDecision decision = firestore.runTransaction(tx -> {
            DocumentSnapshot token = tx.get(result.getTokenRef()).get();
            if (!token.exists() || Boolean.TRUE.equals(token.getBoolean("used"))) {
                return TransactionDecision.conflict("QR code was already used or superseded");
            }
            DocumentSnapshot lock = tx.get(lockRef).get();
            if (lock.exists()) {
                return TransactionDecision.conflict("Student has already been dismissed today");
            }
            DocumentSnapshot studentTx = tx.get(studentRef).get();
            GuardianAuthorizationService.AuthorizationDecision authTx =
                    guardianAuthorizationService.check(studentTx, result.getParentUid());
            if (!authTx.allowed()) {
                return TransactionDecision.forbidden(authTx.reason());
            }
            Map<String, Object> lockData = new HashMap<>();
            lockData.put("schoolId", schoolId);
            lockData.put("studentId", result.getStudentId());
            lockData.put("businessDate", businessDate);
            lockData.put("exitLogId", exitLogRef.getId());
            lockData.put("pickupGateId", gateSnapshot.gateId());
            lockData.put("createdAt", FieldValue.serverTimestamp());
            Map<String, Object> log = buildExitLog(schoolId, result.getStudentId(), result.getParentUid(),
                    verifiedByUid, "qr_scan", businessDate, null, snapshot, gateSnapshot);
            tx.update(result.getTokenRef(), "used", true, "usedAt", FieldValue.serverTimestamp());
            if (authTx.temporary()) {
                tx.update(studentRef,
                        "guardianUids", FieldValue.arrayRemove(result.getParentUid()),
                        "guardians." + result.getParentUid(), FieldValue.delete());
            }
            tx.set(lockRef, lockData);
            tx.set(exitLogRef, log);
            return TransactionDecision.success();
        }).get();

        decision.throwIfRejected();
        return exitLogRef.getId();
    }
    /** Controlled fallback for dead phones, camera failures, or other documented exceptions. */
    public String manualOverride(String studentId, String guardianUid, String reason,
                                 String verifiedByUid, String schoolId)
            throws ExecutionException, InterruptedException {
        return manualOverride(studentId, guardianUid, reason, verifiedByUid, schoolId, null);
    }
    public String manualOverride(String studentId, String guardianUid, String reason,
                                 String verifiedByUid, String schoolId, String pickupGateId)
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
        GuardianAuthorizationService.AuthorizationDecision guardianDecision =
                guardianAuthorizationService.check(student, guardianUid);
        if (!guardianDecision.allowed()) {
            throw new ForbiddenException(guardianDecision.reason());
        }
        String businessDate = LocalDate.now(schoolTimeZone).toString();
        String lockId = safeId(schoolId) + "_" + businessDate + "_" + safeId(studentId);
        DocumentReference lockRef = firestore.collection("dismissalLocks").document(lockId);
        DocumentReference exitLogRef = firestore.collection("exitLogs").document();
        ExitSnapshot snapshot = loadExitSnapshot(studentId, guardianUid, verifiedByUid, schoolId);
        PickupGateSnapshot gateSnapshot = resolvePickupGate(schoolId, pickupGateId, true, verifiedByUid);

        TransactionDecision decision = firestore.runTransaction(tx -> {
            DocumentSnapshot lock = tx.get(lockRef).get();
            if (lock.exists()) {
                return TransactionDecision.conflict("Student has already been dismissed today");
            }
            DocumentSnapshot studentTx = tx.get(studentRef).get();
            GuardianAuthorizationService.AuthorizationDecision guardianDecisionTx =
                    guardianAuthorizationService.check(studentTx, guardianUid);
            if (!guardianDecisionTx.allowed()) {
                return TransactionDecision.forbidden(guardianDecisionTx.reason());
            }
            Map<String, Object> lockData = new HashMap<>();
            lockData.put("schoolId", schoolId);
            lockData.put("studentId", studentId);
            lockData.put("businessDate", businessDate);
            lockData.put("exitLogId", exitLogRef.getId());
            lockData.put("pickupGateId", gateSnapshot.gateId());
            lockData.put("createdAt", FieldValue.serverTimestamp());
            Map<String, Object> log = buildExitLog(schoolId, studentId, guardianUid,
                    verifiedByUid, "manual_override", businessDate, reason.trim(), snapshot, gateSnapshot);
            if (guardianDecisionTx.temporary()) {
                tx.update(studentRef,
                        "guardianUids", FieldValue.arrayRemove(guardianUid),
                        "guardians." + guardianUid, FieldValue.delete());
            }
            tx.set(lockRef, lockData);
            tx.set(exitLogRef, log);
            return TransactionDecision.success();
        }).get();

        decision.throwIfRejected();
        return exitLogRef.getId();
    }
    private Map<String, Object> buildExitLog(String schoolId, String studentId, String parentUid,
                                              String verifiedByUid, String method, String businessDate,
                                              String overrideReason, ExitSnapshot snapshot, PickupGateSnapshot gateSnapshot) {
        Map<String, Object> log = new HashMap<>();
        log.put("schoolId", schoolId);
        log.put("studentId", studentId);
        log.put("parentUid", parentUid);
        log.put("verifiedByUid", verifiedByUid);
        log.put("timestamp", FieldValue.serverTimestamp());
        log.put("businessDate", businessDate);
        log.put("method", method);
        log.put("studentNameSnapshot", snapshot.studentName());
        log.put("studentNumberSnapshot", snapshot.studentNumber());
        log.put("gradeSnapshot", snapshot.grade());
        log.put("sectionSnapshot", snapshot.section());
        log.put("guardianNameSnapshot", snapshot.guardianName());
        log.put("verifiedByNameSnapshot", snapshot.staffName());
        log.put("pickupGateId", gateSnapshot.gateId());
        log.put("pickupGateNameSnapshot", gateSnapshot.gateName());
        log.put("campusId", gateSnapshot.campusId());
        log.put("campusNameSnapshot", gateSnapshot.campusName());
        if (overrideReason != null) log.put("overrideReason", overrideReason);
        return log;
    }
    private ExitSnapshot loadExitSnapshot(String studentId, String guardianUid, String staffUid, String schoolId)
            throws ExecutionException, InterruptedException {
        DocumentSnapshot student = firestore.collection("students").document(studentId).get().get();
        if (!student.exists() || !schoolId.equals(student.getString("schoolId"))) {
            throw new NotFoundException("Student not found in your school");
        }
        DocumentSnapshot guardian = firestore.collection("users").document(guardianUid).get().get();
        DocumentSnapshot staff = firestore.collection("users").document(staffUid).get().get();
        return new ExitSnapshot(
                stringValue(student.getString("fullName"), "Unknown student"),
                stringValue(student.getString("studentNumber"), stringValue(student.getString("lrn"), "")),
                stringValue(student.getString("grade"), ""),
                stringValue(student.getString("section"), ""),
                displayName(guardian, "Unknown guardian"),
                displayName(staff, "Unknown staff")
        );
    }
    private String displayName(DocumentSnapshot user, String fallback) {
        if (user == null || !user.exists()) return fallback;
        return stringValue(user.getString("displayName"), stringValue(user.getString("email"), fallback));
    }

    private String stringValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
    private record ExitSnapshot(String studentName, String studentNumber, String grade, String section,
                                String guardianName, String staffName) { }

    private enum TransactionDecisionType {
        SUCCESS,
        CONFLICT,
        FORBIDDEN
    }

    private record TransactionDecision(TransactionDecisionType type, String message) {
        private static TransactionDecision success() {
            return new TransactionDecision(TransactionDecisionType.SUCCESS, "");
        }

        private static TransactionDecision conflict(String message) {
            return new TransactionDecision(TransactionDecisionType.CONFLICT, message);
        }

        private static TransactionDecision forbidden(String message) {
            return new TransactionDecision(TransactionDecisionType.FORBIDDEN, message);
        }

        private void throwIfRejected() {
            switch (type) {
                case SUCCESS -> { }
                case CONFLICT -> throw new ConflictException(message);
                case FORBIDDEN -> throw new ForbiddenException(message);
            }
        }
    }

    private boolean hasDismissalLock(String schoolId, String studentId)
            throws ExecutionException, InterruptedException {
        String businessDate = LocalDate.now(schoolTimeZone).toString();
        String lockId = safeId(schoolId) + "_" + businessDate + "_" + safeId(studentId);
        return firestore.collection("dismissalLocks").document(lockId).get().get().exists();
    }

    public List<Map<String, Object>> activePickupGates(String schoolId)
            throws ExecutionException, InterruptedException {
        return activePickupGates(schoolId, null);
    }
    /** Returns active pickup gates, optionally restricted by a staff member's assignedPickupGateIds.
     *  A missing/empty assignment keeps backward-compatible access to every active gate. */
    public List<Map<String, Object>> activePickupGates(String schoolId, String staffUid)
            throws ExecutionException, InterruptedException {
        java.util.Set<String> allowedGateIds = null;
        if (staffUid != null && !staffUid.isBlank()) {
            DocumentSnapshot staff = firestore.collection("users").document(staffUid).get().get();
            if (staff.exists() && schoolId.equals(staff.getString("schoolId"))) {
                Object raw = staff.get("assignedPickupGateIds");
                if (raw instanceof java.util.List<?> list && !list.isEmpty()) {
                    allowedGateIds = new java.util.HashSet<>();
                    for (Object value : list) if (value != null) allowedGateIds.add(String.valueOf(value));
                }
            }
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (QueryDocumentSnapshot doc : firestore.collection("pickupGates")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            if (Boolean.FALSE.equals(doc.getBoolean("active"))) continue;
            if (allowedGateIds != null && !allowedGateIds.contains(doc.getId())) continue;
            String campusId = stringValue(doc.getString("campusId"), "");
            String campusName = stringValue(doc.getString("campusName"), "");
            if (!campusId.isBlank()) {
                DocumentSnapshot campus = firestore.collection("campuses").document(campusId).get().get();
                if (!campus.exists() || Boolean.FALSE.equals(campus.getBoolean("active"))) continue;
                campusName = stringValue(campus.getString("name"), campusName);
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", doc.getId());
            item.put("name", stringValue(doc.getString("name"), "Pickup Gate"));
            item.put("campusId", campusId);
            item.put("campusName", campusName);
            item.put("description", stringValue(doc.getString("description"), ""));
            items.add(item);
        }
        items.sort(Comparator.comparing(m -> (String.valueOf(m.get("campusName")) + " " + String.valueOf(m.get("name"))).toLowerCase(Locale.ROOT)));
        return items;
    }
    private PickupGateSnapshot resolvePickupGate(String schoolId, String pickupGateId, boolean requireWhenConfigured, String staffUid)
            throws ExecutionException, InterruptedException {
        String requested = pickupGateId == null ? "" : pickupGateId.trim();
        if (requested.isBlank()) {
            if (requireWhenConfigured && !activePickupGates(schoolId, staffUid).isEmpty()) {
                throw new IllegalArgumentException("Select a pickup gate before approving release");
            }
            return PickupGateSnapshot.none();
        }
        DocumentSnapshot gate = firestore.collection("pickupGates").document(requested).get().get();
        if (!gate.exists() || !schoolId.equals(gate.getString("schoolId")) || Boolean.FALSE.equals(gate.getBoolean("active"))) {
            throw new ForbiddenException("Selected pickup gate is not active for this school");
        }
        if (staffUid != null && !staffUid.isBlank()) {
            DocumentSnapshot staff = firestore.collection("users").document(staffUid).get().get();
            Object raw = staff.exists() ? staff.get("assignedPickupGateIds") : null;
            if (raw instanceof java.util.List<?> list && !list.isEmpty()) {
                boolean allowed = list.stream().anyMatch(v -> requested.equals(String.valueOf(v)));
                if (!allowed) throw new ForbiddenException("You are not assigned to this pickup gate");
            }
        }
        String campusId = stringValue(gate.getString("campusId"), "");
        String campusName = stringValue(gate.getString("campusName"), "");
        if (!campusId.isBlank()) {
            DocumentSnapshot campus = firestore.collection("campuses").document(campusId).get().get();
            if (!campus.exists() || !schoolId.equals(campus.getString("schoolId")) || Boolean.FALSE.equals(campus.getBoolean("active"))) {
                throw new ForbiddenException("The selected pickup gate's campus is inactive");
            }
            campusName = stringValue(campus.getString("name"), campusName);
        }
        return new PickupGateSnapshot(
                gate.getId(),
                stringValue(gate.getString("name"), "Pickup Gate"),
                campusId,
                campusName
        );
    }
    private record PickupGateSnapshot(String gateId, String gateName, String campusId, String campusName) {
        private static PickupGateSnapshot none() { return new PickupGateSnapshot("", "", "", ""); }
    }
    private boolean looksLikeCompactJwt(String value) {
        if (value == null) return false;
        String[] parts = value.trim().split("\\.", -1);
        if (parts.length != 3) return false;
        for (String part : parts) {
            if (part.isBlank() || !part.matches("[A-Za-z0-9_-]+")) {
                return false;
            }
        }
        return true;
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
