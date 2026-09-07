// Shared Firebase client initialization.
// Fill in your project's config values from the Firebase Console.
//
// NOTE: no Cloud Storage import/init here on purpose. As of Feb 3, 2026,
// Cloud Storage for Firebase requires the pay-as-you-go Blaze plan even for
// entirely free-tier usage. This app stores avatars/logos as base64 data
// URIs directly in Firestore instead (see parent/profile.html and the
// backend's SchoolLogoService), so no Storage bucket is needed at all.
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import { getAuth } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import { getFirestore } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

const firebaseConfig = {
  apiKey: "AIzaSyAsNt8BuZfui_Y_6u0KUvtX1OHV6pch3mg",
  authDomain: "pickuppass.firebaseapp.com",
  projectId: "pickuppass",
  messagingSenderId: "445244473897",
  appId: "1:445244473897:web:a810d52df27ba26106d238",
};

export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);

// Base URL of the Java Spring Boot backend.
//
// Auto-detected from how THIS frontend is currently being served, rather
// than a manually maintained comment/uncomment toggle: if you're viewing
// the app via localhost/127.0.0.1 (any local static server such as Live Server,
// `python -m http.server`, `firebase serve`, etc.), it points at your
// local backend. Anywhere else (the deployed Firebase Hosting URL), it
// uses the same-origin /api route, which Firebase Hosting rewrites to Cloud Run.
// This removes the biggest local-dev footgun with the old approach: forgetting to switch
// the hardcoded URL back to the deployed one before running `firebase
// deploy`, which would silently ship a build that only works on your own
// machine.
//
// If your local backend runs on a different port, change LOCAL_API_BASE_URL only.
const LOCAL_API_BASE_URL = "http://localhost:8080/api";
const DEPLOYED_API_BASE_URL = "/api";

const isLocalHost = ["localhost", "127.0.0.1"].includes(window.location.hostname);
export const API_BASE_URL = isLocalHost ? LOCAL_API_BASE_URL : DEPLOYED_API_BASE_URL;

/**
 * School branding (name + logo) is shown in the nav on EVERY page. Reading it
 * from Firestore on every page load would burn a document read per navigation,
 * per user, all day — expensive against the free-tier quota. So we cache it in
 * localStorage keyed by schoolId with a TTL: the nav paints instantly from
 * cache with zero reads, and only hits Firestore when the cache is missing or
 * stale. Call clearSchoolBrandingCache(schoolId) after a logo change so admins
 * see the update immediately instead of waiting out the TTL.
 */
const SCHOOL_CACHE_PREFIX = "pp.school.";
const SCHOOL_CACHE_TTL_MS = 12 * 60 * 60 * 1000; // 12 hours

export async function getSchoolBranding(schoolId, { getDoc, doc } = {}) {
  if (!schoolId) return null;
  const key = SCHOOL_CACHE_PREFIX + schoolId;

  // 1. Fresh cache hit → no Firestore read at all.
  try {
    const cached = JSON.parse(localStorage.getItem(key) || "null");
    if (cached && Date.now() - cached.t < SCHOOL_CACHE_TTL_MS) {
      return { schoolName: cached.schoolName, logoUrl: cached.logoUrl };
    }
  } catch (_) { /* corrupt entry — fall through and refetch */ }

  // 2. Miss/stale → one read, then cache it. Callers pass Firestore's getDoc/doc
  //    (kept out of this module so firebase-init stays import-light).
  if (!getDoc || !doc) return null;
  const snap = await getDoc(doc(db, "schools", schoolId));
  if (!snap.exists()) return null;
  const s = snap.data();
  const branding = { schoolName: s.schoolName || "", logoUrl: s.logoUrl || "" };
  try {
    localStorage.setItem(key, JSON.stringify({ ...branding, t: Date.now() }));
  } catch (_) { /* storage full/blocked — still return the fresh value */ }
  return branding;
}

export function clearSchoolBrandingCache(schoolId) {
  try {
    if (schoolId) localStorage.removeItem(SCHOOL_CACHE_PREFIX + schoolId);
  } catch (_) { /* ignore */ }
}

export async function authedFetch(path, options = {}) {
  const user = auth.currentUser;
  if (!user) throw new Error("Not signed in");
  const idToken = await user.getIdToken();
  const headers = { Authorization: `Bearer ${idToken}`, ...(options.headers || {}) };
  // Let the browser set multipart boundaries for FormData. JSON remains the
  // default for the rest of PickupPass' backend API.
  if (!(options.body instanceof FormData) && !headers["Content-Type"] && !headers["content-type"]) {
    headers["Content-Type"] = "application/json";
  }
  return fetch(`${API_BASE_URL}${path}`, { ...options, headers });
}

const FEEDBACK_STYLE_ID = "pickupPassFeedbackStyles";
const FEEDBACK_REGION_ID = "pickupPassToastRegion";
const RECENT_FEEDBACK_WINDOW_MS = 1400;
const MAX_VISIBLE_FEEDBACK = 4;
const recentFeedback = new Map();
let feedbackSequence = 0;
let inlineFeedbackBridgeInstalled = false;

const feedbackMeta = {
  success: {
    title: "Completed",
    icon: '<path d="M20 6 9 17l-5-5"/>',
    duration: 4800,
  },
  warning: {
    title: "Needs attention",
    icon: '<path d="M10.3 2.9 1.8 17a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 2.9a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4"/><path d="M12 17h.01"/>',
    duration: 6200,
  },
  error: {
    title: "Couldn't complete action",
    icon: '<circle cx="12" cy="12" r="9"/><path d="m9 9 6 6M15 9l-6 6"/>',
    duration: 7200,
  },
  info: {
    title: "Good to know",
    icon: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5"/><path d="M12 8h.01"/>',
    duration: 5200,
  },
};

function ensureFeedbackStyles() {
  if (document.getElementById(FEEDBACK_STYLE_ID)) return;

  const style = document.createElement("style");
  style.id = FEEDBACK_STYLE_ID;
  style.textContent = `
    #${FEEDBACK_REGION_ID} {
      position: fixed;
      z-index: 1200;
      top: max(16px, env(safe-area-inset-top));
      right: max(16px, env(safe-area-inset-right));
      width: min(420px, calc(100vw - 32px));
      display: flex;
      flex-direction: column;
      gap: 10px;
      pointer-events: none;
    }

    .pp-feedback-toast {
      --pp-feedback-accent: var(--primary, #047857);
      --pp-feedback-soft: var(--primary-container, #d1fae5);
      position: relative;
      display: grid;
      grid-template-columns: 42px minmax(0, 1fr) 32px;
      gap: 12px;
      align-items: start;
      overflow: hidden;
      padding: 14px 14px 13px;
      border: 1px solid var(--border, #e5e7eb);
      border: 1px solid color-mix(in srgb, var(--pp-feedback-accent) 24%, var(--border, #e5e7eb));
      border-radius: 16px;
      background: var(--surface, #fff);
      background: color-mix(in srgb, var(--surface, #fff) 96%, var(--pp-feedback-soft));
      color: var(--text, #1f2937);
      box-shadow: 0 18px 45px rgb(15 23 42 / 0.16), 0 3px 10px rgb(15 23 42 / 0.08);
      pointer-events: auto;
      opacity: 0;
      transform: translateY(-8px) scale(.985);
      transition: opacity 180ms ease, transform 180ms ease;
      -webkit-font-smoothing: antialiased;
    }

    .pp-feedback-toast.is-visible { opacity: 1; transform: translateY(0) scale(1); }
    .pp-feedback-toast.is-leaving { opacity: 0; transform: translateY(-5px) scale(.99); }

    .pp-feedback-toast--success {
      --pp-feedback-accent: var(--success, #0f766e);
      --pp-feedback-soft: var(--success-container, #ccfbf1);
    }
    .pp-feedback-toast--warning {
      --pp-feedback-accent: var(--warning, #d97706);
      --pp-feedback-soft: var(--warning-container, #fef3c7);
    }
    .pp-feedback-toast--error {
      --pp-feedback-accent: var(--danger, #dc2626);
      --pp-feedback-soft: var(--danger-container, #fee2e2);
    }
    .pp-feedback-toast--info {
      --pp-feedback-accent: var(--primary, #047857);
      --pp-feedback-soft: var(--primary-container, #d1fae5);
    }

    .pp-feedback-toast__icon {
      width: 42px;
      height: 42px;
      border-radius: 999px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      color: var(--pp-feedback-accent);
      background: var(--pp-feedback-soft);
      background: color-mix(in srgb, var(--pp-feedback-accent) 12%, transparent);
    }
    .pp-feedback-toast__icon svg {
      width: 21px;
      height: 21px;
      fill: none;
      stroke: currentColor;
      stroke-width: 2;
      stroke-linecap: round;
      stroke-linejoin: round;
    }
    .pp-feedback-toast__content { min-width: 0; padding-top: 1px; }
    .pp-feedback-toast__title {
      margin: 0 0 3px;
      color: var(--pp-feedback-accent);
      font: 700 0.875rem/1.25 var(--font-sans, Inter, system-ui, sans-serif);
      letter-spacing: -0.01em;
    }
    .pp-feedback-toast__message {
      margin: 0;
      color: var(--text, #1f2937);
      font: 500 0.875rem/1.45 var(--font-sans, Inter, system-ui, sans-serif);
      overflow-wrap: anywhere;
    }
    .pp-feedback-toast__close {
      width: 32px;
      height: 32px;
      border: 0;
      border-radius: 999px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      background: transparent;
      color: var(--text-muted, #64748b);
      cursor: pointer;
    }
    .pp-feedback-toast__close:hover { background: var(--surface-variant, #f1f5f9); color: var(--text, #1f2937); }
    .pp-feedback-toast__close:focus-visible { outline: 2px solid var(--pp-feedback-accent); outline-offset: 2px; }
    .pp-feedback-toast__close svg { width: 17px; height: 17px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; }

    .pp-feedback-toast__progress {
      position: absolute;
      left: 0;
      right: 0;
      bottom: 0;
      height: 3px;
      background: var(--pp-feedback-soft);
      background: color-mix(in srgb, var(--pp-feedback-accent) 16%, transparent);
    }
    .pp-feedback-toast__progress::after {
      content: "";
      display: block;
      height: 100%;
      width: 100%;
      background: var(--pp-feedback-accent);
      transform-origin: left center;
      animation: pp-feedback-countdown var(--pp-feedback-duration, 5200ms) linear forwards;
    }
    .pp-feedback-toast:hover .pp-feedback-toast__progress::after,
    .pp-feedback-toast:focus-within .pp-feedback-toast__progress::after {
      animation-play-state: paused;
    }

    /* Existing inline alert elements remain valuable next to forms. Give them
       the same premium visual hierarchy so inline and floating feedback never
       look like two unrelated design systems. */
    .pp-alert {
      position: relative;
      align-items: flex-start;
      border-radius: 14px !important;
      border-width: 1px !important;
      box-shadow: 0 5px 18px rgb(15 23 42 / 0.06);
      font-weight: 500;
      line-height: 1.5;
    }

    @keyframes pp-feedback-countdown { from { transform: scaleX(1); } to { transform: scaleX(0); } }

    @media (max-width: 640px) {
      #${FEEDBACK_REGION_ID} {
        left: 12px;
        right: 12px;
        top: max(12px, env(safe-area-inset-top));
        width: auto;
      }
      .pp-feedback-toast {
        grid-template-columns: 38px minmax(0, 1fr) 30px;
        gap: 10px;
        padding: 13px 12px 12px;
        border-radius: 15px;
      }
      .pp-feedback-toast__icon { width: 38px; height: 38px; }
    }

    @media (prefers-reduced-motion: reduce) {
      .pp-feedback-toast { transition: none; transform: none; }
      .pp-feedback-toast__progress::after { animation: none; }
    }
  `;
  document.head.appendChild(style);
}

function normalizeFeedbackType(type) {
  return Object.prototype.hasOwnProperty.call(feedbackMeta, type) ? type : "info";
}

function isDuplicateFeedback(message, type) {
  const now = Date.now();
  const key = `${type}\u0000${message}`;
  const previous = recentFeedback.get(key) || 0;
  recentFeedback.set(key, now);

  for (const [candidate, timestamp] of recentFeedback.entries()) {
    if (now - timestamp > RECENT_FEEDBACK_WINDOW_MS * 3) recentFeedback.delete(candidate);
  }

  return now - previous < RECENT_FEEDBACK_WINDOW_MS;
}

function createFeedbackRegion() {
  ensureFeedbackStyles();

  let region = document.getElementById(FEEDBACK_REGION_ID);
  if (!region) {
    region = document.createElement("div");
    region.id = FEEDBACK_REGION_ID;
    region.setAttribute("aria-live", "polite");
    region.setAttribute("aria-atomic", "false");
    region.setAttribute("aria-label", "PickupPass notifications");
    document.body.appendChild(region);
  }
  return region;
}

function dismissFeedback(toast) {
  if (!toast || toast.dataset.dismissed === "true") return;
  toast.dataset.dismissed = "true";
  toast.classList.add("is-leaving");
  window.setTimeout(() => toast.remove(), 190);
}

/**
 * Canonical, non-blocking feedback for every web screen.
 *
 * Backwards-compatible with the historical showToast(message, type) API, but
 * now renders a premium semantic card with an icon, state title, close affordance,
 * timed progress, mobile-safe positioning, reduced-motion support and duplicate
 * suppression. Callers can optionally provide a custom title/duration.
 */
export function showToast(message, type = "success", options = {}) {
  const normalizedMessage = String(message ?? "").trim();
  if (!normalizedMessage) return null;

  const normalizedType = normalizeFeedbackType(type);
  if (isDuplicateFeedback(normalizedMessage, normalizedType)) return null;

  const meta = feedbackMeta[normalizedType];
  const duration = Number.isFinite(options.duration)
    ? Math.max(1800, options.duration)
    : meta.duration;
  const title = String(options.title || meta.title);
  const region = createFeedbackRegion();

  while (region.children.length >= MAX_VISIBLE_FEEDBACK) {
    region.firstElementChild?.remove();
  }

  const toast = document.createElement("section");
  const toastId = `pp-feedback-${++feedbackSequence}`;
  toast.id = toastId;
  toast.className = `pp-feedback-toast pp-feedback-toast--${normalizedType}`;
  toast.style.setProperty("--pp-feedback-duration", `${duration}ms`);
  toast.setAttribute("role", normalizedType === "error" ? "alert" : "status");
  toast.setAttribute("aria-labelledby", `${toastId}-title`);
  toast.setAttribute("aria-describedby", `${toastId}-message`);

  const iconWrap = document.createElement("span");
  iconWrap.className = "pp-feedback-toast__icon";
  iconWrap.setAttribute("aria-hidden", "true");
  iconWrap.innerHTML = `<svg viewBox="0 0 24 24">${meta.icon}</svg>`;

  const content = document.createElement("div");
  content.className = "pp-feedback-toast__content";

  const heading = document.createElement("p");
  heading.id = `${toastId}-title`;
  heading.className = "pp-feedback-toast__title";
  heading.textContent = title;

  const body = document.createElement("p");
  body.id = `${toastId}-message`;
  body.className = "pp-feedback-toast__message";
  body.textContent = normalizedMessage;

  content.append(heading, body);

  const close = document.createElement("button");
  close.type = "button";
  close.className = "pp-feedback-toast__close";
  close.setAttribute("aria-label", "Dismiss notification");
  close.innerHTML = '<svg viewBox="0 0 24 24"><path d="m7 7 10 10M17 7 7 17"/></svg>';
  close.addEventListener("click", () => dismissFeedback(toast));

  const progress = document.createElement("span");
  progress.className = "pp-feedback-toast__progress";
  progress.setAttribute("aria-hidden", "true");

  toast.append(iconWrap, content, close, progress);
  region.appendChild(toast);

  requestAnimationFrame(() => toast.classList.add("is-visible"));

  let remaining = duration;
  let startedAt = Date.now();
  let timer = window.setTimeout(() => dismissFeedback(toast), remaining);

  const pause = () => {
    if (!timer) return;
    window.clearTimeout(timer);
    timer = null;
    remaining = Math.max(400, remaining - (Date.now() - startedAt));
  };
  const resume = () => {
    if (timer || toast.dataset.dismissed === "true") return;
    startedAt = Date.now();
    timer = window.setTimeout(() => dismissFeedback(toast), remaining);
  };

  toast.addEventListener("mouseenter", pause);
  toast.addEventListener("mouseleave", resume);
  toast.addEventListener("focusin", pause);
  toast.addEventListener("focusout", resume);

  return toast;
}

// Semantic alias for new code. Existing screens can keep importing showToast.
export const showFeedback = showToast;

function feedbackTypeFromAlert(alert) {
  if (alert.classList.contains("pp-alert--danger")) return "error";
  if (alert.classList.contains("pp-alert--warning")) return "warning";
  if (alert.classList.contains("pp-alert--success")) return "success";
  return "info";
}

function maybeBridgeInlineAlert(node) {
  const alert = node?.nodeType === Node.ELEMENT_NODE
    ? (node.matches?.(".pp-alert") ? node : node.closest?.(".pp-alert"))
    : node?.parentElement?.closest?.(".pp-alert");

  if (!alert || alert.dataset.ppNoPopup === "true") return;
  if (alert.hidden || alert.classList.contains("hidden")) return;

  const message = alert.textContent?.trim();
  if (!message) return;

  showToast(message, feedbackTypeFromAlert(alert));
}

const LEGACY_FEEDBACK_ID = /(?:^error|error$|^success|success$|^saveStatus$|^uploadStatus$|^actionStatus$|^formStatus$)/i;
const BUSY_FEEDBACK_TEXT = /^(?:loading|saving|uploading|refreshing|sending|registering|checking|processing|approving|generating|downloading|creating|updating|deleting|revoking|signing|verifying)(?:\b|…|\.{3})/i;

function legacyFeedbackType(element, message) {
  const id = element.id || "";
  if (/error/i.test(id) || /(?:failed|failure|error|could not|couldn't|unable to|invalid)/i.test(message)) return "error";
  if (/warning|attention|couldn't be sent|could not be sent/i.test(message)) return "warning";
  if (/success/i.test(id) || /(?:saved|updated|registered|created|completed|sent|approved|released|removed|deleted|linked)(?:\b|!)/i.test(message)) return "success";
  return "info";
}

function maybeBridgeLegacyFeedback(node) {
  const element = node?.nodeType === Node.ELEMENT_NODE ? node : node?.parentElement;
  if (!element || !element.id || !LEGACY_FEEDBACK_ID.test(element.id)) return;
  if (element.closest?.(`#${FEEDBACK_REGION_ID}`)) return;
  if (element.dataset.ppNoPopup === "true" || element.hidden || element.classList.contains("hidden")) return;

  const message = element.textContent?.trim();
  if (!message || message.length > 600 || BUSY_FEEDBACK_TEXT.test(message)) return;

  showToast(message, legacyFeedbackType(element, message));
}

/**
 * Legacy pages still expose action results by removing `.hidden` from a
 * `.pp-alert`. Bridge those outcomes into the same floating feedback system so
 * every role gets consistent feedback without requiring page-by-page rewrites.
 * `data-pp-no-popup="true"` is an escape hatch for intentionally static notices.
 */
function installInlineFeedbackBridge() {
  if (inlineFeedbackBridgeInstalled || !document.documentElement) return;
  inlineFeedbackBridgeInstalled = true;

  const observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      if (mutation.type === "attributes") {
        maybeBridgeInlineAlert(mutation.target);
        maybeBridgeLegacyFeedback(mutation.target);
      } else if (mutation.type === "characterData") {
        maybeBridgeInlineAlert(mutation.target);
        maybeBridgeLegacyFeedback(mutation.target);
      } else if (mutation.type === "childList") {
        maybeBridgeInlineAlert(mutation.target);
        maybeBridgeLegacyFeedback(mutation.target);
        mutation.addedNodes.forEach((node) => {
          maybeBridgeInlineAlert(node);
          maybeBridgeLegacyFeedback(node);
        });
      }
    }
  });

  observer.observe(document.documentElement, {
    subtree: true,
    childList: true,
    characterData: true,
    attributes: true,
    attributeFilter: ["class", "hidden"],
  });
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", installInlineFeedbackBridge, { once: true });
} else {
  installInlineFeedbackBridge();
}

export function setSubmitButtonBusy(button, isBusy, busyLabel) {
  if (!button) return;
  if (isBusy) {
    button.dataset.idleLabel = button.textContent.trim();
    button.disabled = true;
    button.classList.add("opacity-70", "cursor-not-allowed");
    button.textContent = busyLabel;
  } else {
    button.disabled = false;
    button.classList.remove("opacity-70", "cursor-not-allowed");
    button.textContent = button.dataset.idleLabel || button.textContent;
  }
}
