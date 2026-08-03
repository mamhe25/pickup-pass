/* =============================================================================
   PickupPass — Tailwind (CDN) configuration

   The web app is intentionally buildless: pages open directly in a browser and
   load Tailwind from the CDN. This file wires the CDN's runtime `tailwind`
   global to our design tokens (shared/theme.css) so utility classes like
   `bg-brand-600`, `text-success`, `rounded-md`, `shadow-md`, `font-sans` all
   resolve to the SAME values the Android app uses.

   IMPORTANT — load order in every page <head>:
     1. <link rel="stylesheet" href="../shared/theme.css">   (defines the vars)
     2. <script src="https://cdn.tailwindcss.com"></script>   (defines `tailwind`)
     3. <script src="../shared/tailwind-config.js"></script>  (this file)

   Design intent:
     - We EXTEND rather than replace Tailwind's defaults, so existing pages that
       still use `indigo-600` / `emerald-600` keep working while we migrate them
       to the semantic `brand` / `success` families page by page. Nothing breaks
       mid-migration.
     - Color families point at the raw ramp variables (fixed per family) so the
       Tailwind opacity modifier syntax (e.g. `bg-brand-600/50`) keeps working.
     - Role utilities (`text-success`, `border-default`, `bg-surface`) point at
       the SEMANTIC vars, so they automatically flip in dark mode.
   ========================================================================== */
(function () {
  if (typeof tailwind === "undefined") {
    console.warn(
      "[PickupPass] tailwind-config.js loaded before the Tailwind CDN script. " +
        "Check the <head> load order."
    );
    return;
  }

  tailwind.config = {
    // Dark mode is driven by <html data-theme="dark">; theme.css also honors the
    // OS setting as a fallback for element-level CSS-variable styling.
    darkMode: ["selector", '[data-theme="dark"]'],
    theme: {
      extend: {
        colors: {
          // Brand primary (indigo) — trust & security
          brand: {
            50:  "var(--indigo-50)",
            100: "var(--indigo-100)",
            500: "var(--indigo-500)",
            600: "var(--indigo-600)",
            700: "var(--indigo-700)",
            900: "var(--indigo-900)",
            DEFAULT: "var(--primary)",
          },
          // Success (green) — "safely dismissed", confirmations
          success: {
            500: "var(--green-500)",
            600: "var(--green-600)",
            700: "var(--green-700)",
            900: "var(--green-900)",
            DEFAULT: "var(--success)",
          },
          // Danger (red) — errors & destructive actions only
          danger: {
            500: "var(--red-500)",
            600: "var(--red-600)",
            900: "var(--red-900)",
            DEFAULT: "var(--danger)",
          },
          // Warning (amber) — non-blocking caution only
          warning: {
            50:  "var(--amber-50)",
            500: "var(--amber-500)",
            700: "var(--amber-700)",
            900: "var(--amber-900)",
            DEFAULT: "var(--warning)",
          },
          // Semantic role utilities (auto dark-mode aware)
          surface: {
            DEFAULT: "var(--surface)",
            variant: "var(--surface-variant)",
            sunken:  "var(--surface-sunken)",
          },
          canvas: "var(--bg)",
          ink: {
            strong: "var(--text-strong)",
            DEFAULT: "var(--text)",
            muted:   "var(--text-muted)",
            subtle:  "var(--text-subtle)",
          },
          hairline: {
            DEFAULT: "var(--border)",
            strong:  "var(--border-strong)",
          },
        },
        fontFamily: {
          // Inter everywhere — matches Android's InterFontFamily
          sans: "var(--font-sans)",
        },
        borderRadius: {
          // Mirror Android Shapes.kt tiers
          xs: "var(--radius-xs)",
          sm: "var(--radius-sm)",
          md: "var(--radius-md)",
          lg: "var(--radius-lg)",
          xl: "var(--radius-xl)",
        },
        boxShadow: {
          sm: "var(--shadow-sm)",
          md: "var(--shadow-md)",
          lg: "var(--shadow-lg)",
        },
        spacing: {
          // Named steps that mirror Android Spacing.kt (numeric Tailwind steps
          // still work; these add semantic aliases for the 4px grid).
          xs:  "var(--space-xs)",
          sm:  "var(--space-sm)",
          md:  "var(--space-md)",
          lg:  "var(--space-lg)",
          xl:  "var(--space-xl)",
          xxl: "var(--space-xxl)",
        },
        backgroundImage: {
          "brand-gradient": "var(--brand-gradient)",
        },
        transitionTimingFunction: {
          standard: "var(--ease-standard)",
          emphasized: "var(--ease-emphasized)",
        },
      },
    },
  };
})();
