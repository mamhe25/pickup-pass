# PickupPass Phase 3 Update 15 - Hotfix 1

Fixes Maven compilation errors reported after applying Phase 3 Update 15.

## Fix 1 - DeviceSessionService.ValidationResult record
Java records automatically generate accessors named `allowed()` and `revoked()` for the boolean record components. Static factory methods with those same zero-argument names caused an invalid accessor conflict.

Changed factories to:
- `ValidationResult.allowedResult()`
- `ValidationResult.revokedResult()`

The generated boolean accessor `result.revoked()` used by `DeviceSessionFilter` remains unchanged and now compiles correctly.

## Fix 2 - SaasOperationsHealthService Firestore result type
Firestore `QuerySnapshot#getDocuments()` returns `List<QueryDocumentSnapshot>`, not `List<DocumentSnapshot>`.

Changed the local school list declaration to:
`List<? extends DocumentSnapshot>`

This preserves read-only iteration as `DocumentSnapshot` without unsafe casts or copying.

## After replacing the files
From `pickup-pass-system/backend` run:

```powershell
mvn clean verify
```

No Firestore rules deployment, index deployment, database migration, or environment-variable change is required for this hotfix.
