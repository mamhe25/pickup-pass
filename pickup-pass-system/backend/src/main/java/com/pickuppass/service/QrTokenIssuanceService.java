package com.pickuppass.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.pickuppass.dto.PickupTokenResponse;
import com.pickuppass.exception.ForbiddenException;
import com.pickuppass.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Issues signed, single-use QR pickup tokens for parents.
 *
 * Design:
 *  - The JWT itself carries schoolId/studentId/parentUid/nonce and a short
 *    JWT "exp" (token-ttl-minutes, default 15 min) so a *stale QR code
 *    displayed on screen* stops rendering as valid quickly.
 *  - A separate Firestore doc keyed by the nonce tracks the *dismissal
 *    window* (2 hours from issuedAt) and the single-use "used" flag. This
 *    is the source of truth the QrVerificationService checks — the JWT
 *    signature alone only proves the payload wasn't tampered with, it does
 *    NOT prove the token hasn't already been redeemed.
 *  - Regenerating a pass before the old one is used or expired invalidates
 *    the old nonce, so only the latest QR code on a parent's screen is ever
 *    valid.
 */
@Service
public class QrTokenIssuanceService {

    private final Firestore firestore;
    private final Algorithm hmacAlgorithm;
    private final int tokenTtlMinutes;
    private final int dismissalWindowMinutes;

    public QrTokenIssuanceService(
            Firestore firestore,
            @Value("${qr.signing.secret}") String secret,
            @Value("${qr.token-ttl-minutes:15}") int tokenTtlMinutes,
            @Value("${qr.dismissal-window-minutes:120}") int dismissalWindowMinutes) {
        this.firestore = firestore;
        this.hmacAlgorithm = Algorithm.HMAC256(secret);
        this.tokenTtlMinutes = tokenTtlMinutes;
        this.dismissalWindowMinutes = dismissalWindowMinutes;
    }

    public PickupTokenResponse issueToken(String parentUid, String schoolId, String studentId)
            throws ExecutionException, InterruptedException {

        // 1. Verify the requesting parent is an authorized guardian of this
        //    student. A student can have multiple guardians (primary parent
        //    plus backup contacts); never trust this linkage from the client.
        DocumentReference studentRef = firestore.collection("students").document(studentId);
        DocumentSnapshot studentSnap = studentRef.get().get();

        if (!studentSnap.exists()) {
            throw new NotFoundException("Student not found");
        }
        if (!schoolId.equals(studentSnap.getString("schoolId"))) {
            throw new ForbiddenException("Student does not belong to this school");
        }
        @SuppressWarnings("unchecked")
        java.util.List<String> guardianUids = (java.util.List<String>) studentSnap.get("guardianUids");
        if (guardianUids == null || !guardianUids.contains(parentUid)) {
            throw new ForbiddenException("You are not an authorized guardian for this student");
        }

        // 2. Invalidate any still-unused, still-fresh token previously issued
        //    for this parent+student pair, so only one active QR code exists
        //    at a time (prevents sharing an old screenshot alongside a new one).
        invalidatePriorActiveTokens(schoolId, studentId, parentUid);

        // 3. Mint a new nonce-tracked Firestore doc — this is the single-use
        //    ledger entry the verification service checks at scan time.
        //    A short random ID (not a full 36-char UUID) keeps the signed
        //    JWT — and therefore the QR code encoding it — meaningfully
        //    smaller, which matters for real-world scan reliability on
        //    lower-quality webcams. 96 bits of randomness is still
        //    astronomically collision-safe for one school's token volume.
        String nonce = generateShortId();
        Instant now = Instant.now();
        Instant dismissalDeadline = now.plusSeconds(dismissalWindowMinutes * 60L);

        Map<String, Object> tokenDoc = new HashMap<>();
        tokenDoc.put("schoolId", schoolId);
        tokenDoc.put("studentId", studentId);
        tokenDoc.put("parentUid", parentUid);
        tokenDoc.put("nonce", nonce);
        tokenDoc.put("used", false);
        tokenDoc.put("issuedAt", FieldValue.serverTimestamp());
        tokenDoc.put("dismissalDeadline", Date.from(dismissalDeadline));

        firestore.collection("pickupTokens").document(nonce).set(tokenDoc).get();

        // 4. Sign the JWT the QR code will actually encode. Short "exp" keeps
        //    a displayed-but-idle QR code visually/functionally refreshing
        //    well before the 2-hour dismissal window is reached. Claim names
        //    are deliberately terse (sid/stid/pid/n) — every byte here goes
        //    directly into QR code density.
        Instant jwtExpiry = now.plusSeconds(tokenTtlMinutes * 60L);

        String jwt = JWT.create()
                .withIssuer("pps")
                .withClaim("sid", schoolId)
                .withClaim("stid", studentId)
                .withClaim("pid", parentUid)
                .withClaim("n", nonce)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(jwtExpiry))
                .sign(hmacAlgorithm);

        return new PickupTokenResponse(jwt, Date.from(jwtExpiry), Date.from(dismissalDeadline));
    }

    private String generateShortId() {
        byte[] randomBytes = new byte[12]; // 96 bits
        new java.security.SecureRandom().nextBytes(randomBytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private void invalidatePriorActiveTokens(String schoolId, String studentId, String parentUid)
            throws ExecutionException, InterruptedException {

        ApiFuture<com.google.cloud.firestore.QuerySnapshot> query = firestore.collection("pickupTokens")
                .whereEqualTo("schoolId", schoolId)
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("parentUid", parentUid)
                .whereEqualTo("used", false)
                .get();

        for (DocumentSnapshot doc : query.get().getDocuments()) {
            doc.getReference().update("used", true, "invalidatedReason", "superseded_by_new_token").get();
        }
    }
}
