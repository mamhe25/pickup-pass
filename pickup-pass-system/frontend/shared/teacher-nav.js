// =============================================================================
// PickupPass — shared Teacher navigation
// Sign-out is intentionally kept inside Profile & Security to parallel Android.
// =============================================================================
import { auth, db, getSchoolBranding } from "./firebase-init.js";
import { onAuthStateChanged } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import { doc, getDoc } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";
import { mountThemeToggle, enhancePortal } from './shell.js';
import { mountAccountLink } from './account-link.js';

const NAV_ITEMS = [
  { key: "scanner",       label: "Scanner",       href: "/teacher/scanner.html",       icon: iconScan },
  { key: "students",      label: "Students",      href: "/teacher/students.html",      icon: iconUsers },
  { key: "history",       label: "History",       href: "/teacher/exit-logs.html",     icon: iconClock },
  { key: "operations",    label: "Operations",    href: "/teacher/operations.html",    icon: iconSettings },
  { key: "announce",      label: "Announce",      href: "/teacher/broadcast.html",     icon: iconMegaphone },
  { key: "notifications", label: "Notifications", href: "/teacher/notifications.html", icon: iconBell },
];

function render(mount) {
  ensureTeacherParityStyles();

  const active = mount.dataset.active || "";
  const links = NAV_ITEMS.map((item) => {
    const current = item.key === active ? ' aria-current="page"' : "";
    return `<a class="pp-navlink" href="${item.href}" aria-label="${item.label}"${current}>${item.icon()}<span class="pp-navlink__label">${item.label}</span></a>`;
  }).join("");

  mount.innerHTML = `
    <header class="pp-appbar">
      <div class="pp-appbar__inner">
        <a class="pp-brandmark" href="./scanner.html" aria-label="PickupPass Teacher home">
          <span class="pp-brandmark__badge"><img src="../assets/pickuppass-mark.svg" alt="" /></span>
          <span class="flex flex-col">
            <span class="pp-brandmark__name"><span class="pp-wordmark__pickup">Pickup</span><span class="pp-wordmark__pass">Pass</span></span>
            <span class="pp-brandmark__tag">Teacher</span>
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
      <nav class="pp-navrow" aria-label="Teacher sections">${links}</nav>
    </header>
  `;

  mountThemeToggle(mount.querySelector("[data-pp-theme-toggle]"));
  mountAccountLink(mount);
  enhancePortal();
  enhanceTeacherParity();

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
    } catch (_) { /* school chrome is non-critical */ }
  });
}

function ensureTeacherParityStyles() {
  if (document.querySelector('link[data-pp-teacher-parity]')) return;

  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = "../shared/teacher-parity.css";
  link.dataset.ppTeacherParity = "true";
  document.head.appendChild(link);
}

function enhanceTeacherParity() {
  const themeColor = document.querySelector('meta[name="theme-color"]');
  if (themeColor) themeColor.setAttribute("content", "#4f46e5");

  enhanceScannerPhotoViewer();
  enhanceGuardianCollectionFlow();
}

function enhanceScannerPhotoViewer() {
  const photo = document.getElementById("parentPhoto");
  if (!photo || photo.dataset.ppZoomBound === "true") return;

  photo.dataset.ppZoomBound = "true";
  photo.tabIndex = 0;
  photo.setAttribute("role", "button");
  photo.setAttribute("aria-label", "View guardian identity photo larger");

  const dialog = document.createElement("dialog");
  dialog.className = "pp-scanner-photo-dialog";
  dialog.setAttribute("aria-label", "Guardian identity photo");
  dialog.innerHTML = `
    <div class="pp-scanner-photo-dialog__card">
      <button type="button" class="pp-scanner-photo-dialog__close" aria-label="Close photo">×</button>
      <img class="pp-scanner-photo-dialog__image" alt="Authorized guardian identity photo enlarged" />
      <p class="pp-scanner-photo-dialog__caption">
        Compare this identity photo with the person present before approving release.
      </p>
    </div>
  `;
  document.body.appendChild(dialog);

  const enlarged = dialog.querySelector(".pp-scanner-photo-dialog__image");
  const close = dialog.querySelector(".pp-scanner-photo-dialog__close");

  const openPhoto = () => {
    const source = String(photo.currentSrc || photo.src || "");
    if (!source || source.includes("default-avatar.svg")) return;
    enlarged.src = source;
    if (typeof dialog.showModal === "function") dialog.showModal();
  };

  photo.addEventListener("click", openPhoto);
  photo.addEventListener("keydown", (event) => {
    if (event.key !== "Enter" && event.key !== " ") return;
    event.preventDefault();
    openPhoto();
  });

  close.addEventListener("click", () => dialog.close());
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) dialog.close();
  });
}

function enhanceGuardianCollectionFlow() {
  const guardianList = document.getElementById("guardianList");
  const permanentForm = document.getElementById("permanentForm");
  if (!guardianList || !permanentForm || document.querySelector(".pp-guardian-create-fab")) return;

  const addSection = [...document.querySelectorAll(".pp-staff-guardians-card")]
    .find((section) => section.querySelector(".pp-staff-section-kicker")?.textContent?.includes("ADD PICKUP ACCESS"));

  if (!addSection) return;

  const overlay = document.createElement("div");
  overlay.className = "pp-guardian-create-overlay hidden";
  overlay.setAttribute("role", "dialog");
  overlay.setAttribute("aria-modal", "true");
  overlay.setAttribute("aria-label", "Add pickup guardian");

  const sheet = document.createElement("div");
  sheet.className = "pp-guardian-create-sheet";

  const top = document.createElement("div");
  top.className = "pp-guardian-create-sheet__top";
  top.innerHTML = `
    <strong>Add pickup access</strong>
    <button type="button" class="pp-guardian-create-sheet__close" aria-label="Close add guardian form">×</button>
  `;

  sheet.appendChild(top);
  sheet.appendChild(addSection);
  overlay.appendChild(sheet);
  document.body.appendChild(overlay);

  const fab = document.createElement("button");
  fab.type = "button";
  fab.className = "pp-guardian-create-fab";
  fab.setAttribute("aria-label", "Add pickup guardian");
  fab.title = "Add pickup guardian";
  fab.textContent = "+";
  document.body.appendChild(fab);

  const close = () => {
    overlay.classList.add("hidden");
    document.body.style.overflow = "";
    fab.focus({ preventScroll: true });
  };

  const open = () => {
    overlay.classList.remove("hidden");
    document.body.style.overflow = "hidden";
    requestAnimationFrame(() => {
      const firstEnabled = overlay.querySelector("button:not([disabled]), input:not([disabled]), select:not([disabled])");
      firstEnabled?.focus({ preventScroll: true });
    });
  };

  fab.addEventListener("click", open);
  top.querySelector("button").addEventListener("click", close);
  overlay.addEventListener("click", (event) => {
    if (event.target === overlay) close();
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !overlay.classList.contains("hidden")) close();
  });

  const primaryRequired = document.getElementById("primaryRequired");
  const syncFab = () => {
    if (!primaryRequired) return;
    const primaryMissing = !primaryRequired.classList.contains("hidden");
    fab.hidden = primaryMissing;
    if (primaryMissing && !overlay.classList.contains("hidden")) close();
  };

  if (primaryRequired) {
    new MutationObserver(syncFab).observe(primaryRequired, {
      attributes: true,
      attributeFilter: ["class"]
    });
    syncFab();
  }

  const actionFeedback = document.getElementById("actionFeedback");
  if (actionFeedback) {
    new MutationObserver(() => {
      if (!actionFeedback.classList.contains("hidden") && !overlay.classList.contains("hidden")) {
        close();
      }
    }).observe(actionFeedback, {
      attributes: true,
      attributeFilter: ["class"]
    });
  }
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
function iconScan() { return svg('<path d="M3 7V5a2 2 0 0 1 2-2h2"/><path d="M17 3h2a2 2 0 0 1 2 2v2"/><path d="M21 17v2a2 2 0 0 1-2 2h-2"/><path d="M7 21H5a2 2 0 0 1-2-2v-2"/><path d="M7 12h10"/>'); }
function iconUsers() { return svg('<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>'); }
function iconClock() { return svg('<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>'); }
function iconSettings() { return svg('<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6V21h-4v-.1a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9A1.7 1.7 0 0 0 3 14H3v-4h.1a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 0 0 1.9.3 1.7 1.7 0 0 0 1-1.6V3h4v.1a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.1v4H21a1.7 1.7 0 0 0-1.6 1Z"/>'); }
function iconMegaphone() { return svg('<path d="M3 11v2a1 1 0 0 0 1 1h2l4 4V6L6 10H4a1 1 0 0 0-1 1Z"/><path d="M14 8a4 4 0 0 1 0 8"/>'); }
function iconBell() { return svg('<path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/>'); }

const mount = document.getElementById("teacherNav");
if (mount) render(mount);
