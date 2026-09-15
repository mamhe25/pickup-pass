package com.pickuppass.dto;

import com.google.cloud.firestore.DocumentReference;

public class QrVerificationResult {

    private boolean valid;
    private String message;
    private String studentId;
    private String parentUid;
    private DocumentReference tokenRef;
    private boolean testMode;
    private String operationalMode;

    // Identity data already loaded and validated by QrVerificationService.
    // Returning it with /pickup/verify prevents clients from immediately
    // re-reading the same Firestore documents after a successful scan.
    private String studentName;
    private String studentGrade;
    private String studentSection;
    private String studentNumber;
    private String guardianName;
    private String guardianPhotoUrl;
    private String guardianPhotoValidationStatus;
    private String guardianRelationship;
    private boolean guardianPrimary;

    public static QrVerificationResult fail(String message) {
        QrVerificationResult r = new QrVerificationResult();
        r.valid = false;
        r.message = message;
        return r;
    }

    public static QrVerificationResult success(
            String studentId,
            String parentUid,
            DocumentReference ref,
            boolean testMode,
            String operationalMode,
            String studentName,
            String studentGrade,
            String studentSection,
            String studentNumber,
            String guardianName,
            String guardianPhotoUrl,
            String guardianPhotoValidationStatus,
            String guardianRelationship,
            boolean guardianPrimary) {
        QrVerificationResult r = new QrVerificationResult();
        r.valid = true;
        r.studentId = studentId;
        r.parentUid = parentUid;
        r.tokenRef = ref;
        r.testMode = testMode;
        r.operationalMode = operationalMode;
        r.studentName = studentName;
        r.studentGrade = studentGrade;
        r.studentSection = studentSection;
        r.studentNumber = studentNumber;
        r.guardianName = guardianName;
        r.guardianPhotoUrl = guardianPhotoUrl;
        r.guardianPhotoValidationStatus = guardianPhotoValidationStatus;
        r.guardianRelationship = guardianRelationship;
        r.guardianPrimary = guardianPrimary;
        return r;
    }

    public boolean isValid() { return valid; }
    public String getMessage() { return message; }
    public String getStudentId() { return studentId; }
    public String getParentUid() { return parentUid; }
    public DocumentReference getTokenRef() { return tokenRef; }
    public boolean isTestMode() { return testMode; }
    public String getOperationalMode() { return operationalMode; }
    public String getStudentName() { return studentName; }
    public String getStudentGrade() { return studentGrade; }
    public String getStudentSection() { return studentSection; }
    public String getStudentNumber() { return studentNumber; }
    public String getGuardianName() { return guardianName; }
    public String getGuardianPhotoUrl() { return guardianPhotoUrl; }
    public String getGuardianPhotoValidationStatus() { return guardianPhotoValidationStatus; }
    public String getGuardianRelationship() { return guardianRelationship; }
    public boolean isGuardianPrimary() { return guardianPrimary; }
}
