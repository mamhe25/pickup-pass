# Fix Build Error: Missing Navigation Parameters

The project is failing to compile because `ScannerScreen` and `SchoolBrandingScreen` in `PickupPassNavHost.kt` are missing required parameters that were recently added to these composables.

Specifically:
- `ScannerScreen` is missing `onGoToNotifications` and `onGoToBroadcast`.
- `SchoolBrandingScreen` is missing `onGoToManageSections` and `onGoToBroadcast`.

## Proposed Changes

### [Navigation]

#### [MODIFY] [PickupPassNavHost.kt](file:///D:/Projects/PickupPass/pickup-pass-android/app/src/main/java/com/pickuppass/android/navigation/PickupPassNavHost.kt)
- Update `ScannerScreen` call to include:
    - `onGoToNotifications = { navController.navigate(Screen.ParentNotifications.route) }` (reusing existing notifications route for now).
    - `onGoToBroadcast = { /* TODO: Implement broadcast screen */ }` (empty lambda to fix build).
- Update `SchoolBrandingScreen` call to include:
    - `onGoToManageSections = { /* TODO: Implement manage sections screen */ }` (empty lambda to fix build).
    - `onGoToBroadcast = { /* TODO: Implement broadcast screen */ }` (empty lambda to fix build).

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to ensure the project compiles.
