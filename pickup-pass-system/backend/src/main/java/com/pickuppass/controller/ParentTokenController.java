package com.pickuppass.controller;

import com.pickuppass.dto.PickupTokenResponse;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.QrTokenIssuanceService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parent")
public class ParentTokenController {

    private final QrTokenIssuanceService tokenService;

    public ParentTokenController(QrTokenIssuanceService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping("/generate-token")
    @PreAuthorize("hasRole('parent')")
    public ResponseEntity<PickupTokenResponse> generateToken(
            @RequestBody GenerateTokenRequest req,
            @AuthenticationPrincipal FirebaseUserDetails parent) throws Exception {

        PickupTokenResponse response = tokenService.issueToken(
                parent.getUid(),
                parent.getSchoolId(),
                req.getStudentId()
        );

        return ResponseEntity.ok(response);
    }

    public static class GenerateTokenRequest {
        @NotBlank
        private String studentId;

        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }
    }
}
