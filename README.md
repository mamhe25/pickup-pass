# PickupPass Green Premium Phase 1 - V3

V3 replaces V1/V2.

Why V2 stopped:
`theme.css` has two intentionally similar dark-mode mappings: the explicit
`data-theme="dark"` block and the system `prefers-color-scheme` fallback.
V2's regex was too broad and saw both.

V3 uses ASCII-only literal source blocks, treats each dark block separately by
indentation, normalizes CRLF/LF during preflight, and does not write any source
file unless every expected block is found exactly once.

## Apply

Copy `apply-green-premium-phase-1-v3.ps1` to:

`D:\Projects\PickupPass`

Run:

```powershell
cd D:\Projects\PickupPass
powershell.exe -ExecutionPolicy Bypass -File .\apply-green-premium-phase-1-v3.ps1
```

Then:

```powershell
git diff --check
git status --short
```

Build Android:

```powershell
cd D:\Projects\PickupPass\pickup-pass-android
.\gradlew.bat assembleDebug
```

Do not commit V1/V2/V3 helper scripts. Delete them after verification.
