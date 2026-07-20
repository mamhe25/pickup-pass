package com.pickuppass.dto;

import com.google.cloud.firestore.DocumentReference;

public class QrVerificationResult {

    private boolean valid;
    private String message;
    private String studentId;
    private String parentUid;
    private DocumentReference tokenRef;

    public static QrVerificationResult fail(String message) {
        QrVerificationResult r = new QrVerificationResult();
        r.valid = false;
        r.message = message;
        return r;
    }

    public static QrVerificationResult success(String studentId, String parentUid, DocumentReference ref) {
        QrVerificationResult r = new QrVerificationResult();
        r.valid = true;
        r.studentId = studentId;
        r.parentUid = parentUid;
        r.tokenRef = ref;
        return r;
    }

    public boolean isValid() { return valid; }
    public String getMessage() { return message; }
    public String getStudentId() { return studentId; }
    public String getParentUid() { return parentUid; }
    public DocumentReference getTokenRef() { return tokenRef; }
}
