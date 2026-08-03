# PickupPass Design System

**Safe Dismissal. Peace of Mind.**

The single source of truth for PickupPass's visual language across every platform:
the Android app (Jetpack Compose / Material 3), the web application, and the
future public marketing site. A color, radius, or spacing step means the **same
thing** everywhere — this document is the contract that keeps them aligned.

> Backend is untouched by any design work. This system is presentation-only.

---

## 1. Where the tokens live

| Platform | Location | Form |
|----------|----------|------|
| **Web** (canonical here) | `shared/theme.css` | CSS custom properties |
| Web utilities | `shared/tailwind-config.js` | Maps Tailwind classes → the CSS vars |
| Web components | `shared/components.css` | `pp-` component classes |
| **Android** | `app/src/main/java/com/pickuppass/android/ui/theme/` | `Color.kt`, `Type.kt`, `Shapes.kt`, `Spacing.kt`, `Theme.kt` |

The two platforms were reconciled token-for-token. Every primitive hex in
`theme.css` equals the corresponding value in Android's `Color.kt`.

---

## 2. Brand foundation

**Personality:** professional, reliable, calm, family-oriented, secure.
**Design priority:** trust → clarity → usability → aesthetics.

**Signature:** a disciplined indigo **brand gradient**
(`--brand-gradient`, indigo-600 → indigo-500 → #7C7CF5) used sparingly on the
first-impression surfaces — splash, login header, marketing hero, loading, and
empty-state accents. It is the one place the product "shows its color"; it never
sits behind body text. On web use `.pp-brand-surface` or `bg-brand-gradient`.

---

## 3. Color

### Roles (use these, not raw ramps)

| Role | Meaning | Web semantic var | Web utility | Android role |
|------|---------|------------------|-------------|--------------|
| Primary | Trust / security / primary actions | `--primary` | `brand-600` / `text-brand` | `primary` (Indigo600) |
| Success | "Safely dismissed", confirmations | `--success` | `success` | `secondary` (Green600) |
| Danger | Errors & destructive actions **only** | `--danger` | `danger` | `error` (Red600) |
| Warning | Non-blocking caution **only** | `--warning` | `warning` | Amber500 |
| Canvas | Page background | `--bg` | `bg-canvas` | `background` (Gray50) |
| Surface | Cards, sheets, inputs | `--surface` | `bg-surface` | `surface` (White) |
| Text strong | Headings | `--text-strong` | `text-ink-strong` | `onBackground` (Gray900) |
| Text | Body | `--text` | `text-ink` | Gray800 |
| Text muted | Secondary | `--text-muted` | `text-ink-muted` | `onSurfaceVariant` (Gray700/500) |
| Border | Hairline dividers | `--border` | `border-hairline` | `outline` (Gray200) |

**Discipline rules (shared with Android's `Color.kt` comments):**
- **Red is reserved** exclusively for errors/destructive actions so it never gets diluted.
- **Amber is reserved** exclusively for non-blocking caution (e.g. "created, but the invite email failed") so it never reads as a hard error.
- **Green** signals safety/success — the emotional core of the product.

### Primitive ramps (identical on both platforms)

Indigo `50/100/500/600/700/900` · Green `500/600/700/900` · Red `500/600/900` ·
Amber `50/500/700/900` · Gray `50→900`. See `theme.css` §1 and `Color.kt`.

### Dark mode

Web (`:root[data-theme="dark"]`, with a `prefers-color-scheme` fallback) mirrors
Android `Theme.kt` `DarkColors`: primary lifts to indigo-500, containers use the
**deeper, saturated** 900-tones (Material 3 dark convention) rather than reusing
the light pastels. Toggle on web by setting `<html data-theme="dark">`.

---

## 4. Typography

**Inter** on every platform (Android bundles it as a font resource; web loads the
same family). Full Material 3 type scale — all ~15 roles defined so nothing falls
back to a system default (the drift bug called out in Android's `Type.kt`).

| Role | Size / weight | Web equivalent |
|------|---------------|----------------|
| displayLarge | 40 / Bold | `text-4xl font-bold` |
| headlineSmall | 22 / Bold | `text-xl font-bold` |
| titleMedium | 16 / SemiBold | `text-base font-semibold` |
| bodyMedium | 14 / Normal | `text-sm` |
| labelLarge | 14 / SemiBold | button text |

Web sets `fontFamily.sans` → Inter globally, so all text is on-brand by default.

---

## 5. Radius, spacing, elevation, motion

**Radius** (web `--radius-*` ⇄ Android `Shapes.kt`): xs 8 · sm 12 · md 16 · lg 20 · xl 28 px.
Buttons/inputs = sm; cards/dialogs = md; sheets = xl.

**Spacing** — 4px grid (web `--space-*` ⇄ Android `Spacing.kt`):
xs 4 · sm 8 · md 16 (default) · lg 24 · xl 32 · xxl 48.

**Elevation** — soft, low-contrast shadows (`--shadow-sm/md/lg`). Trust over drama.

**Motion** — calm & quick: fast 120ms, base 200ms, slow 320ms; standard easing
`cubic-bezier(0.2, 0, 0, 1)`. `prefers-reduced-motion` is respected globally.

---

## 6. Components (web `pp-` classes)

Web counterparts of Android's `CommonComponents.kt`. All consume semantic tokens,
so they are automatically dark-mode aware.

- **Buttons** — `pp-btn` + `--primary` / `--secondary` / `--ghost` / `--success` / `--danger` (44px min touch target)
- **Fields** — `pp-field`, `pp-label`
- **Cards** — `pp-card` + `--pad` / `--elevated` / `--interactive`
- **Badges** — `pp-badge` + `--neutral` / `--brand` / `--success` / `--danger` / `--warning`; live `pp-dot`
- **Alerts** — `pp-alert` + `--success` / `--danger` / `--warning` / `--info`
- **Chips** — `pp-chip` (`aria-pressed` / `.is-active`)
- **Skeleton** — `pp-skeleton`; **Empty state** — `pp-empty`; **Brand surface** — `pp-brand-surface`

---

## 7. Using the system on a web page

Load order in `<head>` matters:

```html
<link rel="stylesheet" href="../shared/theme.css" />       <!-- 1. tokens -->
<link rel="stylesheet" href="../shared/components.css" />   <!-- 2. components -->
<script src="https://cdn.tailwindcss.com"></script>         <!-- 3. Tailwind CDN -->
<script src="../shared/tailwind-config.js"></script>        <!-- 4. maps utils→tokens -->
```

The setup stays **buildless** — pages open directly in a browser. `tailwind-config.js`
*extends* Tailwind rather than replacing it, so pages still using legacy
`indigo-600` classes keep working and can migrate family-by-family
(`indigo-*` → `brand-*`, `emerald-*`/`slate-*` → `success`/`ink`) with nothing
breaking mid-migration.

---

## 8. Status: aligned vs. remaining

**Aligned this pass**
- ✅ Canonical token layer (`theme.css`) — mirrors Android primitives exactly
- ✅ Buildless Tailwind mapping (`tailwind-config.js`)
- ✅ Shared component library (`components.css`)
- ✅ Dark mode brought to web (was Android-only)
- ✅ Inter brought to web (was Android-only)
- ✅ `showToast` colors normalized to the canonical palette (emerald→green, slate→gray)
- ✅ Reference refactors: `login.html`, `teacher/students.html` (all IDs & JS preserved)

**Remaining web pages to migrate** (same mechanical pass, all now unblocked):
`parent/*` (4), `school-admin/*` (4), `teacher/*` (6 remaining). Each: add the
4 head tags, swap ad-hoc utilities for `pp-` components + semantic utilities,
update any JS-generated markup, keep every `id`/hook intact.

**Not yet started**
- Public marketing website (does not exist yet — see `PRODUCT_VISION.md`)
- Android is already canonical; no changes were needed this pass. If the brand
  gradient or any semantic tweak here should reflect back, it maps directly to
  `Theme.kt` / `Color.kt`.
