param(
    [switch]$ProductionChecks,
    [switch]$SkipAndroid,
    [switch]$SkipBackend
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root 'pickup-pass-system\backend'
$android = Join-Path $root 'pickup-pass-android'

$failures = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]

function Section([string]$title) {
    Write-Host "`n=== $title ===" -ForegroundColor Cyan
}

function Pass([string]$message) {
    Write-Host "[PASS] $message" -ForegroundColor Green
}

function Warn([string]$message) {
    Write-Host "[WARN] $message" -ForegroundColor Yellow
    $warnings.Add($message)
}

function Fail([string]$message) {
    Write-Host "[FAIL] $message" -ForegroundColor Red
    $failures.Add($message)
}

function Require-File([string]$path, [string]$label) {
    if (Test-Path $path) { Pass $label } else { Fail "$label missing: $path" }
}

Section 'Project structure'
Require-File (Join-Path $backend 'pom.xml') 'Backend pom.xml'
Require-File (Join-Path $backend 'src\main\resources\application-prod.yml') 'Production Spring profile'
Require-File (Join-Path $android 'app\build.gradle.kts') 'Android app Gradle file'
Require-File (Join-Path $backend 'src\main\java\com\pickuppass\security\RateLimitFilter.java') 'Rate limiting filter'
Require-File (Join-Path $backend 'src\main\java\com\pickuppass\service\IdempotencyService.java') 'Idempotency service'
Require-File (Join-Path $backend 'src\main\java\com\pickuppass\service\AuditService.java') 'Audit service'
Require-File (Join-Path $backend 'src\main\java\com\pickuppass\security\SecurityHeadersFilter.java') 'Security headers filter'

if (-not $SkipBackend) {
    Section 'Backend tests'
    Push-Location $backend
    try {
        if (Test-Path '.\mvnw.cmd') {
            & .\mvnw.cmd test
            if ($LASTEXITCODE -eq 0) { Pass 'Backend Maven tests' } else { Fail "Backend Maven tests exited with $LASTEXITCODE" }
        }
        elseif (Get-Command mvn -ErrorAction SilentlyContinue) {
            & mvn test
            if ($LASTEXITCODE -eq 0) { Pass 'Backend Maven tests' } else { Fail "Backend Maven tests exited with $LASTEXITCODE" }
        }
        else {
            Warn 'Maven is not available. Install Maven or add Maven Wrapper, then rerun this script.'
        }
    }
    catch {
        Fail "Backend test command failed: $($_.Exception.Message)"
    }
    finally {
        Pop-Location
    }
}

if (-not $SkipAndroid) {
    Section 'Android build/tests'
    Push-Location $android
    try {
        if (Test-Path '.\gradlew.bat') {
            & .\gradlew.bat testDebugUnitTest assembleDebug
            if ($LASTEXITCODE -eq 0) { Pass 'Android unit tests + debug build' } else { Fail "Android Gradle exited with $LASTEXITCODE" }
        }
        elseif (Get-Command gradle -ErrorAction SilentlyContinue) {
            & gradle testDebugUnitTest assembleDebug
            if ($LASTEXITCODE -eq 0) { Pass 'Android unit tests + debug build' } else { Fail "Android Gradle exited with $LASTEXITCODE" }
        }
        else {
            Warn 'Gradle wrapper/Gradle command is not available. Open the Android project in Android Studio and run Build > Make Project.'
        }
    }
    catch {
        Fail "Android build/test command failed: $($_.Exception.Message)"
    }
    finally {
        Pop-Location
    }
}

if ($ProductionChecks) {
    Section 'Production environment checks'

    $required = @('QR_SIGNING_SECRET','BOOTSTRAP_SECRET','FRONTEND_BASE_URL','CORS_ALLOWED_ORIGINS')
    foreach ($name in $required) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if ([string]::IsNullOrWhiteSpace($value)) {
            Fail "$name is not set"
        } else {
            Pass "$name is set"
        }
    }

    foreach ($name in @('QR_SIGNING_SECRET','BOOTSTRAP_SECRET')) {
        $value = [Environment]::GetEnvironmentVariable($name)
        if (-not [string]::IsNullOrWhiteSpace($value) -and $value.Length -lt 32) {
            Fail "$name must be at least 32 characters"
        }
    }

    $cors = [Environment]::GetEnvironmentVariable('CORS_ALLOWED_ORIGINS')
    if ($cors -match '\*') { Fail 'CORS_ALLOWED_ORIGINS must not contain wildcard * in production' }
    if ($cors -match 'localhost|127\.0\.0\.1') { Fail 'CORS_ALLOWED_ORIGINS contains a local development origin' }

    $frontend = [Environment]::GetEnvironmentVariable('FRONTEND_BASE_URL')
    if ($frontend -and -not $frontend.StartsWith('https://')) { Fail 'FRONTEND_BASE_URL should use HTTPS in production' }

    $profile = [Environment]::GetEnvironmentVariable('SPRING_PROFILES_ACTIVE')
    if ($profile -ne 'prod') { Warn 'SPRING_PROFILES_ACTIVE is not prod for this shell.' } else { Pass 'SPRING_PROFILES_ACTIVE=prod' }
}

Section 'Result'
if ($warnings.Count -gt 0) {
    Write-Host "$($warnings.Count) warning(s)." -ForegroundColor Yellow
}
if ($failures.Count -gt 0) {
    Write-Host "$($failures.Count) failure(s). Fix these before production deployment." -ForegroundColor Red
    exit 1
}

Write-Host 'Phase 1 verification completed with no detected failures.' -ForegroundColor Green
exit 0
