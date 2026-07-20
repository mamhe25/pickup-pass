package com.pickuppass.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.pickuppass.dto.QrVerificationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class QrVerificationService {

    private final Firestore firestore;
    private final Algorithm hmacAlgorithm;

    public QrVerificationService(Firestore firestore,
                                  @Value("${qr.signing.secret}") String secret) {
        this.firestore = firestore;
        this.hmacAlgorithm = Algorithm.HMAC256(secret);
    }

    public QrVerificationResult verify(String qrToken, String scanningSchoolId)
            throws ExecutionException, InterruptedException {

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

        if (!schoolId.equals(scanningSchoolId)) {
            return QrVerificationResult.fail("QR code does not belong to this school");
        }

        DocumentReference tokenRef = firestore.collection("pickupTokens").document(nonce);
        DocumentSnapshot tokenSnap = tokenRef.get().get();

        if (!tokenSnap.exists()) {
            return QrVerificationResult.fail("Unknown or revoked token");
        }
        if (Boolean.TRUE.equals(tokenSnap.getBoolean("used"))) {
            return QrVerificationResult.fail("QR code already used or superseded");
        }

        Timestamp issuedAt = tokenSnap.getTimestamp("issuedAt");
        if (issuedAt != null) {
            long ageMillis = System.currentTimeMillis() - issuedAt.toDate().getTime();
            if (ageMillis > TimeUnit.HOURS.toMillis(2)) {
                return QrVerificationResult.fail("Dismissal window (2 hours) has expired");
            }
        }

        DocumentSnapshot studentSnap = firestore.collection("students").document(studentId).get().get();
        if (!studentSnap.exists() || !scanningSchoolId.equals(studentSnap.getString("schoolId"))) {
            return QrVerificationResult.fail("Student not found in this school");
        }

        return QrVerificationResult.success(studentId, parentUid, tokenRef);
    }

    public void markUsedAndLog(QrVerificationResult result, String verifiedByUid, String schoolId)
            throws java.util.concurrent.ExecutionException, InterruptedException {

        // Awaited (not fire-and-forget): the guard's "Approve Release" tap
        // must not report success unless the token was actually marked used
        // and the exit log actually landed — otherwise a write failure here
        // would silently leave a reusable token and no audit trail while
        // still showing "Release Logged" on screen.
        result.getTokenRef().update("used", true, "usedAt", FieldValue.serverTimestamp()).get();

        Map<String, Object> log = new HashMap<>();
        log.put("schoolId", schoolId);
        log.put("studentId", result.getStudentId());
        log.put("parentUid", result.getParentUid());
        log.put("verifiedByUid", verifiedByUid);
        log.put("timestamp", FieldValue.serverTimestamp());
        log.put("method", "qr_scan");
        firestore.collection("exitLogs").add(log).get();
    }
}
