# Fix Compose BOM Resolution Error

The project sync is failing because `androidx.compose:compose-bom:2026.06.01` cannot be resolved. This version appears to be inconsistent with the version used in the main application dependencies (`2024.06.00`) and may not be available in the configured repositories.

## Proposed Changes

### [app module](file:///D:/Projects/PickupPass/pickup-pass-android/app/build.gradle.kts)

#### [MODIFY] [build.gradle.kts](file:///D:/Projects/PickupPass/pickup-pass-android/app/build.gradle.kts)
- Align the `androidTestImplementation` Compose BOM version with the `implementation` version (`2024.06.00`).
- This ensures consistency between production and test environments and uses a version known to resolve correctly.

## Verification Plan

### Automated Tests
- Run `gradle_sync` to verify that all dependencies are resolved.
- Run a simple build or test task if needed.

### Manual Verification
- Confirm that the project sync finishes without the "Failed to resolve" error.
