PickupPass Premium Green Brand Foundation

This replaces the failed V1/V2/V3 PowerShell scripts and malformed Git patch.

Why this version is safer:
- Python reads UTF-8 consistently on Windows.
- It only changes exact current color/value strings already verified in the repo.
- It preflights every replacement before writing any file.
- It intentionally keeps the existing token names (`indigo-*`, `green-*`) for
  Phase 1 so the existing web Tailwind bridge and Android Material theme keep
  working without a token-renaming migration.
- It is safe to rerun after a successful application.

Files changed:
1. pickup-pass-system/frontend/shared/theme.css
2. pickup-pass-system/frontend/shared/portal.css
3. pickup-pass-system/frontend/login.html
4. pickup-pass-android/app/src/main/java/com/pickuppass/android/ui/theme/Color.kt

Apply:
  cd D:\Projects\PickupPass
  py .\apply-green-premium-phase-1.py

Verify:
  git diff --check
  git status --short

Android:
  cd D:\Projects\PickupPass\pickup-pass-android
  .\gradlew.bat assembleDebug

Do not commit the helper Python file. Delete it after the source changes are verified.
