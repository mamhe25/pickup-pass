// =============================================================================
// PickupPass — shared Parent navigation
//
// The Parent counterpart of teacher-nav.js / school-admin-nav.js. Parents are
// the least technical audience, so the shell stays deliberately simple: a
// sticky brand app-bar + three destinations (Students, Notifications, Profile).
//
// It centrally owns sign-out and the signed-in email, and — an improvement over
// the old design where the unread bell lived only on the home page — it fetches
// the unread notification count and shows a badge on the Notifications link on
// EVERY parent page.
//
// Usage in a page:
//   <div id="parentNav" data-active="students"></div>          (top of <body>)
//   <script type="module" src="../shared/parent-nav.js"></script>
//
// data-active values: students | notifications | profile
// Drill-down pages (pickup-pass, manage-guardians) use data-active="students"
// and add their own contextual back link in the body.
// =============================================================================
import { auth, db, getSchoolBranding } from "./firebase-init.js";
import { onAuthStateChanged, signOut } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import { collection, query, where, getDocs, doc, getDoc } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";
import { mountThemeToggle, enhancePortal } from './shell.js';

const NAV_ITEMS = [
  { key: "students",      label: "My Students",   href: "./students.html",          icon: iconUsers },
  { key: "guardians",     label: "Guardians",     href: "./manage-guardians.html",  icon: iconShield },
  { key: "devices",       label: "Devices",       href: "./devices.html",           icon: iconDevice },
  { key: "notifications", label: "Notifications", href: "./notifications.html",     icon: iconBell, badge: true },
  { key: "profile",       label: "My Profile",    href: "./profile.html",           icon: iconUser },
];

function render(mount) {
  const active = mount.dataset.active || "";

  const links = NAV_ITEMS.map((item) => {
    const current = item.key === active ? ' aria-current="page"' : "";
    const badge = item.badge ? '<span id="navUnreadBadge" class="pp-navlink__badge hidden"></span>' : "";
    return `<a class="pp-navlink" href="${item.href}" aria-label="${item.label}"${current}>${item.icon()}<span class="pp-navlink__label">${item.label}</span>${badge}</a>`;
  }).join("");

  mount.innerHTML = `
    <header class="pp-appbar">
      <div class="pp-appbar__inner">
        <a class="pp-brandmark" href="./students.html" aria-label="PickupPass home">
          <span class="pp-brandmark__badge">${iconShield()}</span>
          <span class="flex flex-col">
            <span class="pp-brandmark__name">PickupPass</span>
            <span class="pp-brandmark__tag">Family</span>
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
      <nav class="pp-navrow" aria-label="Parent sections">${links}</nav>
    </header>
  `;

  mountThemeToggle(mount.querySelector("[data-pp-theme-toggle]"));
  enhancePortal();

  mount.querySelector("#signOutBtn").addEventListener("click", async () => {
    await signOut(auth);
    window.location.href = "../login.html";
  });

  onAuthStateChanged(auth, async (user) => {
    if (!user) {
      window.location.href = "../login.html";
      return;
    }
    const emailEl = mount.querySelector("#currentUserEmail");
    if (emailEl) emailEl.textContent = user.email || "";
    updateUnreadBadge(user.uid);
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

// Unread notification count → badge on the Notifications link. Same query the
// old home-page bell used; fetching the unread docs and counting client-side is
// cheap for a single guardian's inbox and needs no aggregation index.
async function updateUnreadBadge(uid) {
  const badge = document.getElementById("navUnreadBadge");
  if (!badge) return;
  try {
    const snap = await getDocs(query(
      collection(db, "notifications"),
      where("recipientUid", "==", uid),
      where("read", "==", false)
    ));
    if (snap.size > 0) {
      badge.textContent = snap.size > 9 ? "9+" : String(snap.size);
      badge.classList.remove("hidden");
    } else {
      badge.classList.add("hidden");
    }
  } catch (err) {
    // A badge is non-critical chrome — never let it break the page.
    badge.classList.add("hidden");
  }
}

function svg(paths) {
  return `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths}</svg>`;
}
function iconUsers() { return svg('<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>'); }
function iconBell() { return svg('<path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/>'); }
function iconDevice() { return svg('<rect x="5" y="2" width="14" height="20" rx="2"/><path d="M9 18h6"/>'); }
function iconUser() { return svg('<circle cx="12" cy="8" r="4"/><path d="M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1"/>'); }
function iconShield() {
  return `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
    <path d="M12 2 4 5v6c0 4.4 3.1 8.4 8 9.6 4.9-1.2 8-5.2 8-9.6V5l-8-3Z" fill="white" fill-opacity="0.2"/>
    <path d="M9.5 12.2l1.8 1.8 3.5-3.7" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  </svg>`;
}

const mount = document.getElementById("parentNav");
if (mount) render(mount);
