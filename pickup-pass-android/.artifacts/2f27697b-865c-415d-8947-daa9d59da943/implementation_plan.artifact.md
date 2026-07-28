# Implementation Plan - Fix Conflicting Overloads in TeacherBroadcastScreen

The project has two identical files named `TeacherBroadcastScreen.kt` and `TeacherBroadcastViewModel.kt` in different directories but with the same package declaration (`com.pickuppass.android.ui.teacher.broadcast`). This causes a "Conflicting overloads" error during compilation.

The files in the `schooladmin` directory were likely intended for school admin broadcasts but were not properly renamed or updated.

## Proposed Changes

### UI Components (School Admin)

#### [MODIFY] [TeacherBroadcastScreen.kt](file:///D:/Projects/PickupPass/pickup-pass-android/app/src/main/java/com/pickuppass/android/ui/schooladmin/broadcast/TeacherBroadcastScreen.kt) -> Rename to `SchoolAdminBroadcastScreen.kt`
- Update package to `com.pickuppass.android.ui.schooladmin.broadcast`.
- Rename Composable to `SchoolAdminBroadcastScreen`.
- Update ViewModel reference to `SchoolAdminBroadcastViewModel`.
- Update text content to be appropriate for a school admin.

#### [MODIFY] [TeacherBroadcastViewModel.kt](file:///D:/Projects/PickupPass/pickup-pass-android/app/src/main/java/com/pickuppass/android/ui/schooladmin/broadcast/TeacherBroadcastViewModel.kt) -> Rename to `SchoolAdminBroadcastViewModel.kt`
- Update package to `com.pickuppass.android.ui.schooladmin.broadcast`.
- Rename class to `SchoolAdminBroadcastViewModel`.
- Rename UI state to `SchoolAdminBroadcastUiState`.
- Inject `SchoolAdminRepository` instead of `TeacherRepository`.
- Update `send` method to use `schoolAdminRepository.broadcastToSchool`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to ensure the conflict is resolved and the project builds.

### Manual Verification
- None (refactoring to fix build error).
