// =============================================================================
// PickupPass — shared School Admin navigation
// Sign-out lives in Profile & Security, matching the Android account pattern.
// =============================================================================
import { auth, db, getSchoolBranding } from "./firebase-init.js";
import { onAuthStateChanged } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import { doc, getDoc } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";
import { mountThemeToggle, enhancePortal } from './shell.js';
import { mountAccountLink } from './account-link.js';

const NAV_ITEMS = [
  { key: "home",      label: "Dashboard",  href: "/school-admin/dashboard.html",          icon: iconHome },
  { key: "students",  label: "Students",   href: "/school-admin/students-lifecycle.html", icon: iconUsers },
  { key: "academics", label: "Academics",  href: "/school-admin/academics.html",          icon: iconGrid },
  { key: "staff",     label: "Staff",      href: "/school-admin/staff.html",              icon: iconUserPlus },
  { key: "pickup",    label: "Pickup",     href: "/school-admin/pickup-settings.html",    icon: iconShield },
  { key: "reports",   label: "Reports",    href: "/school-admin/reports.html",            icon: iconReport },
  { key: "announce",  label: "Announce",   href: "/school-admin/broadcast.html",          icon: iconMegaphone },
  { key: "billing",   label: "Billing",    href: "/school-admin/billing.html",            icon: iconBilling },
  { key: "audit",     label: "Audit",      href: "/school-admin/audit.html",              icon: iconClock },
  { key: "launch",    label: "Launch",     href: "/school-admin/launch-readiness.html",   icon: iconRocket },
  { key: "branding",  label: "Branding",   href: "/school-admin/branding.html",           icon: iconPalette },
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
        <a class="pp-brandmark" href="/school-admin/dashboard.html" aria-label="PickupPass Admin home">
          <span class="pp-brandmark__badge"><img src="../assets/pickuppass-mark.svg" alt="" /></span>
          <span class="flex flex-col">
            <span class="pp-brandmark__name"><span class="pp-wordmark__pickup">Pickup</span><span class="pp-wordmark__pass">Pass</span></span>
            <span class="pp-brandmark__tag">Admin</span>
          </span>
        </a>
        <div class="flex items-center gap-3">
          <span id="currentUserEmail" class="text-xs text-ink-subtle hidden sm:inline"></span>
          <button data-pp-theme-toggle class="pp-icon-btn" type="button"></button>
        </div>
      </div>
      <div id="navSchoolSlot" class="pp-appbar__schoolband hidden">
        <img id="navSchoolLogo" alt="" />
        <span id="navSchoolName"></span>
      </div>
      <nav class="pp-navrow" aria-label="School admin sections">${links}</nav>
    </header>
  `;

  mountThemeToggle(mount.querySelector("[data-pp-theme-toggle]"));
  mountAccountLink(mount);
  enhancePortal();

  onAuthStateChanged(auth, async (user) => {
    if (!user) {
      window.location.href = "/login.html";
      return;
    }
    const emailEl = mount.querySelector("#currentUserEmail");
    if (emailEl) emailEl.textContent = user.email || "";
    try {
      const tokenResult = await user.getIdTokenResult();
      await loadSchoolIdentity(mount, tokenResult.claims.schoolId);
    } catch (_) { /* school chrome is non-critical */ }
  });
}

async function loadSchoolIdentity(mount, schoolId) {
  if (!schoolId) return;
  const school = await getSchoolBranding(schoolId, { getDoc, doc });
  if (!school) return;
  const slot = mount.querySelector("#navSchoolSlot");
  const nameEl = mount.querySelector("#navSchoolName");
  const logoEl = mount.querySelector("#navSchoolLogo");
  if (nameEl) nameEl.textContent = school.schoolName || "";
  if (logoEl) {
    if (school.logoUrl) logoEl.src = school.logoUrl;
    else logoEl.remove();
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
function iconUsers() { return svg('<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/>'); }
function iconReport() { return svg('<path d="M4 19V9"/><path d="M10 19V5"/><path d="M16 19v-7"/><path d="M22 19H2"/>'); }
function iconBilling() { return svg('<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 10h18"/><path d="M7 15h3"/>'); }
function iconClock() { return svg('<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>'); }
function iconRocket() { return svg('<path d="M4 13c-1.5 1.2-2 3-2 5 2 0 3.8-.5 5-2"/><path d="M10 14 5 9c2.5-4.5 7-7 12.5-7 .3 5.5-2.5 10-7 12Z"/><circle cx="14" cy="7" r="1.5"/><path d="m9 15-1 5 5-1"/>'); }
function iconPalette() { return svg('<path d="M12 3a9 9 0 1 0 0 18h1.5a2 2 0 0 0 0-4H12a2 2 0 0 1 0-4h4a5 5 0 0 0 0-10Z"/><circle cx="7.5" cy="10" r=".5"/><circle cx="9" cy="6.5" r=".5"/>'); }
function iconShield() { return svg('<path d="M12 3 4 6v5c0 5 3.4 9 8 10 4.6-1 8-5 8-10V6l-8-3Z"/><path d="m9 12 2 2 4-4"/>'); }

const mount = document.getElementById("adminNav");
if (mount) render(mount);
