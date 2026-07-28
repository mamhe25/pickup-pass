# Fix for "Failed to get service from broker" SecurityException

The error `java.lang.SecurityException: Unknown calling package name 'com.google.android.gms'` with tag `GoogleApiManager` typically occurs when an Android app attempts to interact with Google Play Services (such as Firebase or ML Kit) but the system's service broker rejects the connection. This is often due to package visibility restrictions introduced in Android 11 (API 30) or missing/incorrect metadata in the app's manifest.

## User Review Required

> [!IMPORTANT]
> This fix assumes the application is running on a device or emulator with Google Play Services installed. If the device lacks Play Services, the app will still fail, but with a different error.

## Proposed Changes

### [Component Name] Manifest Configuration

We will update the `AndroidManifest.xml` to ensure full compatibility with Google Play Services on Android 11+ and provide the necessary visibility and metadata.

#### [MODIFY] [AndroidManifest.xml](file:///D:/Projects/PickupPass/pickup-pass-android/app/src/main/AndroidManifest.xml)

1.  **Add `<queries>` block**: Explicitly declare visibility for the Google Play Services package. This is required for apps targeting API 30+ that bind to Play Services.
2.  **Add `ACCESS_NETWORK_STATE` permission**: Required by many Google Play Services libraries to check connectivity before making requests.
3.  **Add `com.google.android.gms.version` meta-data**: While typically injected by the `google-services` plugin, explicitly adding it can resolve issues where the version is not correctly identified during the broker's security check.

## Verification Plan

### Automated Tests
- I will verify the manifest merges correctly (mentally, as I can't run a full build/merge check here easily, but I can check for syntax).

### Manual Verification
- Deploy the app to the device/emulator where the error was occurring.
- Verify that features using Google Play Services (Firebase Auth, FCM token registration, or ML Kit scanning) now work without throwing the `SecurityException`.
- Check logcat for the `GoogleApiManager` tag to confirm successful connection.
