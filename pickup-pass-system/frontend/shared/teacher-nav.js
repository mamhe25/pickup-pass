// =============================================================================
// PickupPass — shared Teacher navigation
//
// One navigation shell for the whole Teacher persona, so every teacher page is
// unmistakably the same product. Replaces the old per-page hand-rolled headers
// (which each said something different: "← Students", "Students →", etc.).
//
// It renders a sticky brand app-bar + a scrollable pill nav — the web mirror of
// the Android app's bottom navigation — and CENTRALLY owns two things every page
// used to re-implement: sign-out and the signed-in user's email.
//
// Usage in a page:
//   <div id="teacherNav" data-active="students"></div>            (top of <body>)
//   <script type="module" src="../shared/teacher-nav.js"></script> (before the
//                                                     page's own inline module)
//
// data-active values: scanner | students | history | announce | notifications
// Drill-down pages (register-parent, manage-guardians) use data-active="students"
// and add their own contextual back link in the page body.
// =============================================================================
import { auth, db, getSchoolBranding } from "./firebase-init.js";
import { onAuthStateChanged, signOut } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import { doc, getDoc } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";
import { mountThemeToggle, enhancePortal } from './shell.js';

// Primary destinations, in the order a teacher's day tends to flow.
const NAV_ITEMS = [
  { key: "scanner",       label: "Scanner",       href: "/teacher/scanner.html",       icon: iconScan },
  { key: "students",      label: "Students",      href: "/teacher/students.html",      icon: iconUsers },
  { key: "history",       label: "History",       href: "/teacher/exit-logs.html",     icon: iconClock },
  { key: "operations",    label: "Operations",    href: "/teacher/operations.html",    icon: iconSettings },
  { key: "announce",      label: "Announce",      href: "/teacher/broadcast.html",     icon: iconMegaphone },
  { key: "notifications", label: "Notifications", href: "/teacher/notifications.html", icon: iconBell },
];

function render(mount) {
  const active = mount.dataset.active || "";

  const links = NAV_ITEMS.map((item) => {
    const current = item.key === active ? ' aria-current="page"' : "";
    // aria-label keeps the destination announced even when the label is
    // visually hidden (icons-only) on phones.
    return `<a class="pp-navlink" href="${item.href}" aria-label="${item.label}"${current}>${item.icon()}<span class="pp-navlink__label">${item.label}</span></a>`;
  }).join("");

  mount.innerHTML = `
    <header class="pp-appbar">
      <div class="pp-appbar__inner">
        <a class="pp-brandmark" href="./scanner.html" aria-label="PickupPass Teacher home">
          <span class="pp-brandmark__badge">${iconShield()}</span>
          <span class="flex flex-col">
            <span class="pp-brandmark__name">PickupPass</span>
            <span class="pp-brandmark__tag">Teacher</span>
          </span>
        </a>
        <div class="flex items-center gap-3">
          <span id="currentUserEmail" class="text-xs text-ink-subtle hidden sm:inline"></span>
          <button data-pp-theme-toggle class="pp-icon-btn" type="button"></button><button id="signOutBtn" class="pp-btn pp-btn--ghost" type="button">Sign out</button>
        </div>
      </div>
      <div id="navSchoolSlot" class="pp-appbar__schoolband hidden">
        <img id="navSchoolLogo" alt="" />
        <span id="navSchoolName"></span>
      </div>
      <nav class="pp-navrow" aria-label="Teacher sections">${links}</nav>
    </header>
  `;

  mountThemeToggle(mount.querySelector("[data-pp-theme-toggle]"));
  enhancePortal();

  const signOutBtn = mount.querySelector("#signOutBtn");
  signOutBtn.addEventListener("click", async () => {
    await signOut(auth);
    window.location.href = "../login.html";
  });

  // Central auth guard + email + school identity. Individual pages keep their
  // own onAuthStateChanged for data loading; this one only fills the shared
  // chrome, so both coexist.
  onAuthStateChanged(auth, async (user) => {
    if (!user) {
      window.location.href = "../login.html";
      return;
    }
    const emailEl = mount.querySelector("#currentUserEmail");
    if (emailEl) emailEl.textContent = user.email || "";
    try {
      const tokenResult = await user.getIdTokenResult();
      await loadSchoolIdentity(mount, tokenResult.claims.schoolId);
    } catch (_) { /* school chrome is non-critical — never block the page */ }
  });
}

// Fills the always-present school band below the app-bar. Branding comes from a
// localStorage TTL cache (see getSchoolBranding), so this normally costs zero
// Firestore reads — it only reads once per ~12h per device.
async function loadSchoolIdentity(mount, schoolId) {
  if (!schoolId) return;
  const school = await getSchoolBranding(schoolId, { getDoc, doc });
  if (!school) return;
  const slot = mount.querySelector("#navSchoolSlot");
  const nameEl = mount.querySelector("#navSchoolName");
  const logoEl = mount.querySelector("#navSchoolLogo");
  if (nameEl) nameEl.textContent = school.schoolName || "";
  if (logoEl) {
    if (school.logoUrl) { logoEl.src = school.logoUrl; }
    else { logoEl.remove(); }
  }
  if (slot) { slot.classList.remove("hidden"); slot.classList.add("flex"); }
}

// Icons — inline, currentColor, 16px. Kept tiny and consistent with the brand's
// calm, simple line style.
function svg(paths) {
  return `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths}</svg>`;
}
function iconScan() { return svg('<path d="M3 7V5a2 2 0 0 1 2-2h2"/><path d="M17 3h2a2 2 0 0 1 2 2v2"/><path d="M21 17v2a2 2 0 0 1-2 2h-2"/><path d="M7 21H5a2 2 0 0 1-2-2v-2"/><path d="M7 12h10"/>'); }
function iconUsers() { return svg('<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>'); }
function iconClock() { return svg('<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>'); }
function iconSettings() { return svg('<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6V21h-4v-.1a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H3v-4h.1a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3 1.7 1.7 0 0 0 1-1.6V3h4v.1a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.1v4H21a1.7 1.7 0 0 0-1.6 1Z"/>'); }
function iconMegaphone() { return svg('<path d="M3 11v2a1 1 0 0 0 1 1h2l4 4V6L6 10H4a1 1 0 0 0-1 1Z"/><path d="M14 8a4 4 0 0 1 0 8"/>'); }
function iconBell() { return svg('<path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/>'); }
function iconShield() {
  return `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <path d="M12 2 4 5v6c0 4.4 3.1 8.4 8 9.6 4.9-1.2 8-5.2 8-9.6V5l-8-3Z" fill="white" fill-opacity="0.2"/>
    <path d="M9.5 12.2l1.8 1.8 3.5-3.7" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  </svg>`;
}

const mount = document.getElementById("teacherNav");
if (mount) render(mount);
