from pathlib import Path
import sys

REPO = Path(r"D:\Projects\PickupPass")

changes = {
    Path("pickup-pass-system/frontend/shared/theme.css"): [
        ("--indigo-50:  #EEF2FF;", "--indigo-50:  #ECFDF5;", 1),
        ("--indigo-100: #E0E7FF;", "--indigo-100: #D1FAE5;", 1),
        ("--indigo-500: #6366F1;", "--indigo-500: #10B981;", 1),
        ("--indigo-600: #4F46E5;", "--indigo-600: #047857;", 1),
        ("--indigo-700: #4338CA;", "--indigo-700: #065F46;", 1),
        ("--indigo-900: #312E81;", "--indigo-900: #064E3B;", 1),

        ("--green-500: #22C55E;", "--green-500: #2DD4BF;", 1),
        ("--green-600: #16A34A;", "--green-600: #0F766E;", 1),
        ("--green-700: #15803D;", "--green-700: #115E59;", 1),
        ("--green-900: #14532D;", "--green-900: #134E4A;", 1),

        ("--success-container:  #ECFDF5;", "--success-container:  #F0FDFA;", 1),
        ("--success-hover:      #4ADE80;", "--success-hover:      #5EEAD4;", 2),
        ("--on-success-container: #BBF7D0;", "--on-success-container: #CCFBF1;", 2),

        (
            "--focus-ring: color-mix(in srgb, var(--indigo-600) 45%, transparent);",
            "--focus-ring: color-mix(in srgb, var(--indigo-500) 48%, transparent);",
            1,
        ),
        (
            "--brand-gradient: linear-gradient(135deg, var(--indigo-600) 0%, var(--indigo-500) 55%, #7C7CF5 100%);",
            "--brand-gradient: linear-gradient(135deg, var(--indigo-900) 0%, var(--indigo-700) 52%, #10B981 100%);",
            1,
        ),
    ],

    Path("pickup-pass-system/frontend/shared/portal.css"): [
        (
            "background:linear-gradient(90deg,var(--primary),var(--indigo-500));",
            "background:linear-gradient(90deg,var(--primary),var(--indigo-500),#84CC16);",
            1,
        ),
        (
            "background:linear-gradient(145deg,#312e81,#4f46e5 56%,#6366f1);",
            "background:radial-gradient(360px 300px at 100% 100%,rgba(132,204,22,.28),transparent 72%),linear-gradient(145deg,#064E3B,#065F46 52%,#047857);",
            1,
        ),
        (
            "background:rgba(255,255,255,.11)}",
            "background:rgba(255,255,255,.07);box-shadow:0 0 90px rgba(16,185,129,.22)}",
            1,
        ),
    ],

    Path("pickup-pass-system/frontend/login.html"): [
        (
            '<meta name="theme-color" content="#4F46E5">',
            '<meta name="theme-color" content="#047857">',
            1,
        ),
    ],

    Path("pickup-pass-android/app/src/main/java/com/pickuppass/android/ui/theme/Color.kt"): [
        ("val Indigo50 = Color(0xFFEEF2FF)", "val Indigo50 = Color(0xFFECFDF5)", 1),
        ("val Indigo100 = Color(0xFFE0E7FF)", "val Indigo100 = Color(0xFFD1FAE5)", 1),
        ("val Indigo500 = Color(0xFF6366F1)", "val Indigo500 = Color(0xFF10B981)", 1),
        ("val Indigo600 = Color(0xFF4F46E5)", "val Indigo600 = Color(0xFF047857)", 1),
        ("val Indigo700 = Color(0xFF4338CA)", "val Indigo700 = Color(0xFF065F46)", 1),
        ("val Indigo900 = Color(0xFF312E81)", "val Indigo900 = Color(0xFF064E3B)", 1),
        ("val Green500 = Color(0xFF22C55E)", "val Green500 = Color(0xFF2DD4BF)", 1),
        ("val Green600 = Color(0xFF16A34A)", "val Green600 = Color(0xFF0F766E)", 1),
        ("val Green700 = Color(0xFF15803D)", "val Green700 = Color(0xFF115E59)", 1),
        ("val Green900 = Color(0xFF14532D)", "val Green900 = Color(0xFF134E4A)", 1),
    ],
}

if not (REPO / ".git").exists():
    sys.exit(f"ERROR: not a Git repository: {REPO}")

prepared = {}

print("PickupPass - Premium Green Brand Foundation")
print(f"Repository: {REPO}")
print()
print("Preflight: checking exact current source values...")

for rel, replacements in changes.items():
    path = REPO / rel
    if not path.exists():
        sys.exit(f"ERROR: missing file: {rel}")

    text = path.read_text(encoding="utf-8")

    for old, new, expected_count in replacements:
        old_count = text.count(old)
        new_count = text.count(new)

        # Make repeat runs safe.
        if old_count == 0 and new_count == expected_count:
            continue

        if old_count != expected_count:
            sys.exit(
                f"ERROR: preflight failed in {rel}\n"
                f"Expected {expected_count} occurrence(s) of:\n  {old}\n"
                f"Found: {old_count}\n"
                "No files were changed."
            )

        text = text.replace(old, new)

    prepared[path] = text

print("Preflight passed. Applying 4 source files...")

for path, text in prepared.items():
    path.write_text(text, encoding="utf-8", newline="")
    print(f"UPDATED  {path.relative_to(REPO)}")

print()
print("SUCCESS: premium green brand foundation applied.")
print()
print("Next:")
print("  git diff --check")
print("  git status --short")
print(r"  cd D:\Projects\PickupPass\pickup-pass-android")
print(r"  .\gradlew.bat assembleDebug")
