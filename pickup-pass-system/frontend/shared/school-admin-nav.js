// =============================================================================
// PickupPass — shared School Admin navigation
//
// The School Admin counterpart of teacher-nav.js: one navigation shell for the
// whole persona so every admin page is unmistakably the same product. Renders
// the sticky brand app-bar + scrollable pill nav, and centrally owns sign-out
// and the signed-in email (previously re-implemented on every page).
//
// Usage in a page:
//   <div id="adminNav" data-active="home"></div>                     (top of <body>)
//   <script type="module" src="../shared/school-admin-nav.js"></script>
//
// data-active values: home | staff | sections | announce
// =============================================================================
import { auth, db, getSchoolBranding } from "./firebase-init.js";
import { onAuthStateChanged, signOut } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import { doc, getDoc } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

const NAV_ITEMS = [
  { key: "home",     label: "Home",     href: "./branding.html",       icon: iconHome },
  { key: "staff",    label: "Staff",    href: "./staff.html",          icon: iconUserPlus },
  { key: "sections", label: "Sections", href: "./manage-sections.html", icon: iconGrid },
  { key: "announce", label: "Announce", href: "./broadcast.html",      icon: iconMegaphone },
];

function render(mount) {
  const active = mount.dataset.active || "";

  const links = NAV_ITEMS.map((item) => {
    const current = item.key === active ? ' aria-current="page"' : "";
    return `<a class="pp-navlink" href="${item.href}" aria-label="${item.label}"${current}>${item.icon()}<span class="pp-navlink__label">${item.label}</span></a>`;
  }).join("");

  mount.innerHTML = `
    <header class="pp-appbar">
      <div class="pp-appbar__inner">
        <a class="pp-brandmark" href="./branding.html" aria-label="PickupPass Admin home">
          <span class="pp-brandmark__badge">${iconShield()}</span>
          <span class="flex flex-col">
            <span class="pp-brandmark__name">PickupPass</span>
            <span class="pp-brandmark__tag">Admin</span>
          </span>
        </a>
        <div class="flex items-center gap-3">
          <span id="currentUserEmail" class="text-xs text-ink-subtle hidden sm:inline"></span>
          <button id="signOutBtn" class="pp-btn pp-btn--ghost" type="button">Sign out</button>
        </div>
      </div>
      <div id="navSchoolSlot" class="pp-appbar__schoolband hidden">
        <img id="navSchoolLogo" alt="" />
        <span id="navSchoolName"></span>
      </div>
      <nav class="pp-navrow" aria-label="School admin sections">${links}</nav>
    </header>
  `;

  mount.querySelector("#signOutBtn").addEventListener("click", async () => {
    await signOut(auth);
    window.location.href = "../login.html";
  });

  // Central auth guard + email + school identity. Pages keep their own
  // onAuthStateChanged for data loading; this one only fills the shared chrome.
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

// Fills the always-present school band below the app-bar, from the localStorage
// TTL cache (see getSchoolBranding) — normally zero Firestore reads.
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

function svg(paths) {
  return `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths}</svg>`;
}
function iconHome() { return svg('<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/><path d="M9 21v-6h6v6"/>'); }
function iconUserPlus() { return svg('<path d="M14 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="8" cy="7" r="4"/><path d="M19 8v6"/><path d="M22 11h-6"/>'); }
function iconGrid() { return svg('<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>'); }
function iconMegaphone() { return svg('<path d="M3 11v2a1 1 0 0 0 1 1h2l4 4V6L6 10H4a1 1 0 0 0-1 1Z"/><path d="M14 8a4 4 0 0 1 0 8"/>'); }
function iconShield() {
  return `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <path d="M12 2 4 5v6c0 4.4 3.1 8.4 8 9.6 4.9-1.2 8-5.2 8-9.6V5l-8-3Z" fill="white" fill-opacity="0.2"/>
    <path d="M9.5 12.2l1.8 1.8 3.5-3.7" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  </svg>`;
}

const mount = document.getElementById("adminNav");
if (mount) render(mount);
