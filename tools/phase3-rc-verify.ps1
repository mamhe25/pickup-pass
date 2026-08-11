param(
    [switch]$SkipBuilds
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Failures = New-Object System.Collections.Generic.List[string]
$Warnings = New-Object System.Collections.Generic.List[string]
$Passes = New-Object System.Collections.Generic.List[string]

function Pass([string]$Message) { $Passes.Add($Message) }
function Fail([string]$Message) { $Failures.Add($Message) }
function Warn([string]$Message) { $Warnings.Add($Message) }

function Require-File([string]$Relative) {
    $p = Join-Path $Root $Relative
    if (Test-Path $p -PathType Leaf) { Pass "Present: $Relative"; return $p }
    Fail "Missing required file: $Relative"
    return $null
}

function Require-Contains([string]$Relative, [string]$Needle, [string]$Message) {
    $p = Join-Path $Root $Relative
    if (!(Test-Path $p)) { Fail "Missing required file: $Relative"; return }
    $text = Get-Content $p -Raw
    if ($text.Contains($Needle)) { Pass $Message } else { Fail "$Message (not found in $Relative)" }
}

function Require-NotContains([string]$Relative, [string]$Needle, [string]$Message) {
    $p = Join-Path $Root $Relative
    if (!(Test-Path $p)) { Fail "Missing required file: $Relative"; return }
    $text = Get-Content $p -Raw
    if (!$text.Contains($Needle)) { Pass $Message } else { Fail "$Message (unsafe value found in $Relative)" }
}

Write-Host "PickupPass Phase 3 Release Candidate Verification" -ForegroundColor Cyan
Write-Host "Project root: $Root"

Require-File "pickup-pass-system/backend/pom.xml" | Out-Null
Require-File "pickup-pass-android/app/build.gradle.kts" | Out-Null
Require-File "pickup-pass-system/firestore.rules" | Out-Null
Require-File "production.env.example" | Out-Null

# Firestore client-write hardening.
Require-Contains "pickup-pass-system/firestore.rules" "request.resource.data.diff(resource.data).affectedKeys().hasOnly(['photoUrl'])" "User self-update is restricted to avatar only"
Require-Contains "pickup-pass-system/firestore.rules" "match /pickupTokens/{tokenId}" "Pickup token rules are present"
Require-Contains "pickup-pass-system/firestore.rules" "allow read, write: if false;" "Pickup tokens are backend-only"
Require-Contains "pickup-pass-system/firestore.rules" "allow create, update, delete: if false;" "Backend-owned collections reject direct client mutations"

# Bootstrap must be disabled for normal production operation.
Require-Contains "pickup-pass-system/backend/src/main/resources/application.yml" 'enabled: ${BOOTSTRAP_ENABLED:false}' "Bootstrap defaults OFF"
Require-Contains "production.env.example" "BOOTSTRAP_ENABLED=false" "Production example keeps bootstrap OFF"
Require-Contains "pickup-pass-system/backend/src/main/java/com/pickuppass/controller/BootstrapController.java" "if (!bootstrapEnabled)" "Bootstrap endpoint checks explicit enable flag"

# Release network security.
Require-NotContains "pickup-pass-android/app/src/main/res/xml/network_security_config.xml" 'cleartextTrafficPermitted="true"' "Release/main Android config rejects cleartext"
Require-Contains "pickup-pass-android/app/src/debug/res/xml/network_security_config.xml" 'cleartextTrafficPermitted="true"' "Debug-only local HTTP allowlist exists"

# Docker release build must run tests.
Require-Contains "pickup-pass-system/backend/Dockerfile" "mvn clean verify -B" "Production image build runs backend tests"
Require-NotContains "pickup-pass-system/backend/Dockerfile" "-DskipTests" "Production Docker build does not skip tests"

# Production base API URL must be HTTPS.
$gradle = Get-Content (Join-Path $Root "pickup-pass-android/app/build.gradle.kts") -Raw
$defaultApi = [regex]::Match($gradle, 'buildConfigField\("String",\s*"API_BASE_URL",\s*"\\"(https://[^"]+)\\""\)')
if ($defaultApi.Success) { Pass "Default/release API base URL is HTTPS" } else { Fail "Could not verify an HTTPS default/release API_BASE_URL" }

# Parse configuration JSON.
foreach ($rel in @(
    "pickup-pass-system/firestore.indexes.json",
    "pickup-pass-system/firebase/firestore.indexes.json"
)) {
    $p = Join-Path $Root $rel
    if (Test-Path $p) {
        try { Get-Content $p -Raw | ConvertFrom-Json | Out-Null; Pass "Valid JSON: $rel" }
        catch { Fail "Invalid JSON: $rel" }
    }
}

# Secret/signing material should not live in source control.
$dangerous = Get-ChildItem $Root -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object {
        $_.Length -gt 0 -and (
            $_.Extension -in @(".pem", ".p12", ".jks", ".keystore") -or
            $_.FullName -match '[\\/]backend[\\/]secrets[\\/]'
        )
    }
if ($dangerous.Count -gt 0) {
    foreach ($f in $dangerous) { Fail "Credential/signing material found in project tree: $($f.FullName)" }
} else {
    Pass "No non-empty service-account/signing credential files found in project tree"
}

# Block common private-key material in text files without printing the matched secret.
$privateKeyHit = Get-ChildItem $Root -Recurse -File -Include *.json,*.yml,*.yaml,*.properties,*.txt,*.md -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike "phase3-rc-verify.*" } |
    Select-String -SimpleMatch "BEGIN PRIVATE KEY" -List -ErrorAction SilentlyContinue
if ($privateKeyHit) { Fail "Private-key material detected in project text files" } else { Pass "No embedded private-key block detected" }

# Optional local builds.
if (!$SkipBuilds) {
    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mvn) {
        Push-Location (Join-Path $Root "pickup-pass-system/backend")
        try {
            & mvn test -B
            if ($LASTEXITCODE -eq 0) { Pass "Backend Maven tests passed" } else { Fail "Backend Maven tests failed" }
        } finally { Pop-Location }
    } else {
        Warn "Maven not installed; backend build/test was not executed by this script"
    }

    $gradlew = Join-Path $Root "pickup-pass-android/gradlew.bat"
    if (Test-Path $gradlew) {
        Push-Location (Join-Path $Root "pickup-pass-android")
        try {
            & $gradlew :app:assembleDebug
            if ($LASTEXITCODE -eq 0) { Pass "Android debug build passed" } else { Fail "Android debug build failed" }
        } finally { Pop-Location }
    } else {
        Warn "Gradle wrapper not present; Android build was not executed by this script"
    }
}

Write-Host ""
Write-Host "PASS: $($Passes.Count)" -ForegroundColor Green
foreach ($p in $Passes) { Write-Host "  [PASS] $p" -ForegroundColor Green }

if ($Warnings.Count -gt 0) {
    Write-Host "WARN: $($Warnings.Count)" -ForegroundColor Yellow
    foreach ($w in $Warnings) { Write-Host "  [WARN] $w" -ForegroundColor Yellow }
}

if ($Failures.Count -gt 0) {
    Write-Host "FAIL: $($Failures.Count)" -ForegroundColor Red
    foreach ($f in $Failures) { Write-Host "  [FAIL] $f" -ForegroundColor Red }
    exit 1
}

Write-Host "Release-candidate static gate passed." -ForegroundColor Green
exit 0
