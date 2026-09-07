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
const FEEDBACK_CONSUMED_CLASS = "pp-feedback-inline-consumed";
const RECENT_FEEDBACK_WINDOW_MS = 1400;
const recentFeedback = new Map();
let feedbackSequence = 0;
let inlineFeedbackBridgeInstalled = false;

const feedbackMeta = {
  success: {
    title: "Success",
    icon: '<path d="M20 6 9 17l-5-5"/>',
  },
  warning: {
    title: "Action required",
    icon: '<path d="M10.3 2.9 1.8 17a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 2.9a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4"/><path d="M12 17h.01"/>',
  },
  error: {
    title: "Something went wrong",
    icon: '<circle cx="12" cy="12" r="9"/><path d="m9 9 6 6M15 9l-6 6"/>',
  },
  info: {
    title: "Information",
    icon: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5"/><path d="M12 8h.01"/>',
  },
};

function ensureFeedbackStyles() {
  if (document.getElementById(FEEDBACK_STYLE_ID)) return;

  const style = document.createElement("style");
  style.id = FEEDBACK_STYLE_ID;
  style.textContent = `
    .pp-feedback-inline-consumed {
      display: none !important;
    }

    #${FEEDBACK_REGION_ID} {
      position: fixed;
      z-index: 1400;
      inset: 0;
      display: grid;
      place-items: center;
      padding: max(20px, env(safe-area-inset-top)) max(20px, env(safe-area-inset-right)) max(20px, env(safe-area-inset-bottom)) max(20px, env(safe-area-inset-left));
      background: rgb(15 23 42 / 0.48);
      backdrop-filter: blur(4px);
      -webkit-backdrop-filter: blur(4px);
      pointer-events: auto;
    }

    #${FEEDBACK_REGION_ID}:empty {
      display: none;
    }

    .pp-feedback-toast {
      --pp-feedback-accent: var(--primary, #047857);
      --pp-feedback-soft: var(--primary-container, #d1fae5);
      position: relative;
      width: min(440px, calc(100vw - 32px));
      max-height: min(86vh, 620px);
      overflow: auto;
      padding: 52px 30px 30px;
      border: 1px solid var(--border, #e5e7eb);
      border: 1px solid color-mix(in srgb, var(--pp-feedback-accent) 16%, var(--border, #e5e7eb));
      border-radius: 24px;
      background: var(--surface, #fff);
      color: var(--text, #1f2937);
      box-shadow: 0 32px 90px rgb(15 23 42 / 0.28), 0 12px 30px rgb(15 23 42 / 0.16);
      opacity: 0;
      transform: translateY(10px) scale(.975);
      transition: opacity 180ms ease, transform 220ms cubic-bezier(.2,.75,.25,1);
      -webkit-font-smoothing: antialiased;
    }

    .pp-feedback-toast.is-visible {
      opacity: 1;
      transform: translateY(0) scale(1);
    }

    .pp-feedback-toast.is-leaving {
      opacity: 0;
      transform: translateY(5px) scale(.985);
    }

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
      width: 76px;
      height: 76px;
      margin: 0 auto 20px;
      border-radius: 999px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      color: var(--pp-feedback-accent);
      background: var(--pp-feedback-soft);
      background: color-mix(in srgb, var(--pp-feedback-accent) 10%, transparent);
      box-shadow: inset 0 0 0 9px color-mix(in srgb, var(--pp-feedback-accent) 6%, transparent);
    }

    .pp-feedback-toast__icon svg {
      width: 34px;
      height: 34px;
      fill: none;
      stroke: currentColor;
      stroke-width: 2;
      stroke-linecap: round;
      stroke-linejoin: round;
    }

    .pp-feedback-toast__content {
      min-width: 0;
      text-align: center;
    }

    .pp-feedback-toast__title {
      margin: 0 0 9px;
      color: var(--text, #1f2937);
      font: 700 1.28rem/1.3 var(--font-sans, Inter, system-ui, sans-serif);
      letter-spacing: -0.025em;
    }

    .pp-feedback-toast__message {
      max-width: 36ch;
      margin: 0 auto;
      color: var(--text-muted, #64748b);
      font: 500 0.94rem/1.6 var(--font-sans, Inter, system-ui, sans-serif);
      overflow-wrap: anywhere;
    }

    .pp-feedback-toast__accent {
      width: 36px;
      height: 4px;
      margin: 20px auto 0;
      border-radius: 999px;
      background: var(--pp-feedback-accent);
    }

    .pp-feedback-toast__close {
      position: absolute;
      top: 10px;
      right: 10px;
      width: 40px;
      height: 40px;
      border: 0;
      border-radius: 999px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      background: var(--surface-variant, #f1f5f9);
      color: var(--text-muted, #64748b);
      cursor: pointer;
      transition: transform 140ms ease, background 140ms ease, color 140ms ease;
    }

    .pp-feedback-toast__close:hover {
      background: color-mix(in srgb, var(--pp-feedback-accent) 8%, var(--surface-variant, #f1f5f9));
      color: var(--text, #1f2937);
      transform: scale(1.04);
    }

    .pp-feedback-toast__close:focus-visible {
      outline: 2px solid var(--pp-feedback-accent);
      outline-offset: 2px;
    }

    .pp-feedback-toast__close svg {
      width: 18px;
      height: 18px;
      fill: none;
      stroke: currentColor;
      stroke-width: 2;
      stroke-linecap: round;
    }

    /* Old action-result containers are still updated by legacy screens for
       compatibility, but once bridged they must not remain as duplicate text
       below a password field, above a table, or inside a card. Busy/progress
       labels are deliberately not consumed. */
    .${FEEDBACK_CONSUMED_CLASS} {
      display: none !important;
    }

    .pp-alert {
      position: relative;
      align-items: flex-start;
      border-radius: 14px !important;
      border-width: 1px !important;
      box-shadow: 0 5px 18px rgb(15 23 42 / 0.06);
      font-weight: 500;
      line-height: 1.5;
    }

    @media (max-width: 640px) {
      #${FEEDBACK_REGION_ID} {
        padding: max(16px, env(safe-area-inset-top)) 16px max(16px, env(safe-area-inset-bottom));
      }

      .pp-feedback-toast {
        width: min(100%, 420px);
        padding: 50px 22px 26px;
        border-radius: 22px;
      }

      .pp-feedback-toast__icon {
        width: 70px;
        height: 70px;
        margin-bottom: 18px;
      }

      .pp-feedback-toast__title {
        font-size: 1.16rem;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .pp-feedback-toast,
      .pp-feedback-toast__close {
        transition: none;
        transform: none;
      }
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
    region.setAttribute("aria-label", "PickupPass feedback");
    region.addEventListener("click", (event) => {
      if (event.target === region) dismissFeedback(region.firstElementChild);
    });
    document.body.appendChild(region);
  }
  return region;
}

function dismissFeedback(toast) {
  if (!toast || toast.dataset.dismissed === "true") return;
  toast.dataset.dismissed = "true";
  toast.classList.add("is-leaving");
  toast._ppCleanup?.();
  window.setTimeout(() => {
    const restoreFocus = toast._ppRestoreFocus;
    toast.remove();
    if (restoreFocus instanceof HTMLElement && document.contains(restoreFocus)) {
      restoreFocus.focus({ preventScroll: true });
    }
  }, 190);
}

/**
 * Canonical centered feedback dialog for every web screen.
 *
 * The historical showToast API is retained so existing screens need no rewrite,
 * but terminal action outcomes now render as a premium modal with a dimmed
 * backdrop, semantic hero icon, centered content, close affordance, focus
 * management, Escape/backdrop dismissal, reduced-motion support and duplicate
 * suppression. Feedback remains visible until the user dismisses it.
 */
export function showToast(message, type = "success", options = {}) {
  const normalizedMessage = String(message ?? "").trim();
  if (!normalizedMessage) return null;

  const normalizedType = normalizeFeedbackType(type);
  if (isDuplicateFeedback(normalizedMessage, normalizedType)) return null;

  const meta = feedbackMeta[normalizedType];
  const title = String(options.title || meta.title);
  const region = createFeedbackRegion();

  // One action result at a time. A second result supersedes the first instead
  // of stacking multiple modal scrims/cards on top of each other.
  while (region.firstElementChild) {
    region.firstElementChild._ppCleanup?.();
    region.firstElementChild.remove();
  }

  const toast = document.createElement("section");
  const toastId = `pp-feedback-${++feedbackSequence}`;
  toast.id = toastId;
  toast.className = `pp-feedback-toast pp-feedback-toast--${normalizedType}`;
  toast.setAttribute("role", normalizedType === "error" ? "alertdialog" : "dialog");
  toast.setAttribute("aria-modal", "true");
  toast.setAttribute("aria-labelledby", `${toastId}-title`);
  toast.setAttribute("aria-describedby", `${toastId}-message`);
  toast.tabIndex = -1;
  toast._ppRestoreFocus = document.activeElement;

  const close = document.createElement("button");
  close.type = "button";
  close.className = "pp-feedback-toast__close";
  close.setAttribute("aria-label", "Close message");
  close.innerHTML = '<svg viewBox="0 0 24 24"><path d="m7 7 10 10M17 7 7 17"/></svg>';
  close.addEventListener("click", () => dismissFeedback(toast));

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

  const accent = document.createElement("span");
  accent.className = "pp-feedback-toast__accent";
  accent.setAttribute("aria-hidden", "true");

  content.append(heading, body, accent);
  toast.append(close, iconWrap, content);
  region.appendChild(toast);

  const onKeyDown = (event) => {
    if (event.key === "Escape") {
      event.preventDefault();
      dismissFeedback(toast);
    }
  };
  document.addEventListener("keydown", onKeyDown);
  toast._ppCleanup = () => document.removeEventListener("keydown", onKeyDown);

  requestAnimationFrame(() => {
    toast.classList.add("is-visible");
    close.focus({ preventScroll: true });
  });

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

const BUSY_FEEDBACK_TEXT = /^(?:loading|saving|uploading|refreshing|sending|registering|checking|processing|approving|generating|downloading|creating|updating|deleting|revoking|signing|verifying)(?:\b|…|\.{3})/i;
const LEGACY_FEEDBACK_SELECTOR = [
  ".form-status",
  ".save-status",
  "#statusMsg",
  "#uploadStatus",
  "#actionStatus",
  "#formStatus",
  "#accountStatus",
  "#mfaStatus",
  "#actionFeedback",
  "#successMsg",
  "#errorMsg",
  "#formError",
  "#formSuccess",
  "#dialogError",
].join(",");

function inlineFeedbackElement(node, selector) {
  if (!node) return null;
  const element = node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement;
  if (!element) return null;
  return element.matches?.(selector) ? element : element.closest?.(selector);
}

function shouldKeepInline(element, message) {
  if (!element || element.dataset.ppNoPopup === "true") return true;
  if (BUSY_FEEDBACK_TEXT.test(message)) return true;
  return false;
}

function resetConsumedState(element) {
  element?.classList?.remove(FEEDBACK_CONSUMED_CLASS);
}

function consumeInlineFeedback(element, message, type) {
  if (!message || message.length > 600) {
    resetConsumedState(element);
    return;
  }

  if (shouldKeepInline(element, message)) {
    resetConsumedState(element);
    return;
  }

  showToast(message, type);
  element.classList.add(FEEDBACK_CONSUMED_CLASS);
}

function maybeBridgeInlineAlert(node) {
  const alert = inlineFeedbackElement(node, ".pp-alert");
  if (!alert || alert.dataset.ppNoPopup === "true") return;

  if (alert.hidden || alert.classList.contains("hidden")) {
    resetConsumedState(alert);
    return;
  }

  const message = alert.textContent?.trim() || "";
  consumeInlineFeedback(alert, message, feedbackTypeFromAlert(alert));
}

function legacyFeedbackType(element, message) {
  const id = element.id || "";
  if (/error/i.test(id) || /(?:failed|failure|error|could not|couldn't|unable to|invalid)/i.test(message)) return "error";
  if (/warning|attention|couldn't be sent|could not be sent/i.test(message)) return "warning";
  if (/success/i.test(id) || /(?:saved|updated|registered|created|completed|sent|approved|released|removed|deleted|linked|enabled|disabled)(?:\b|!)/i.test(message)) return "success";
  return "info";
}

function maybeBridgeLegacyFeedback(node) {
  const element = inlineFeedbackElement(node, LEGACY_FEEDBACK_SELECTOR);
  if (!element || element.matches?.(".pp-alert")) return;
  if (element.closest?.(`#${FEEDBACK_REGION_ID}`)) return;
  if (element.dataset.ppNoPopup === "true") return;

  if (element.hidden || element.classList.contains("hidden")) {
    resetConsumedState(element);
    return;
  }

  const message = element.textContent?.trim() || "";
  consumeInlineFeedback(element, message, legacyFeedbackType(element, message));
}

/**
 * Legacy screens still update inline action-result elements. Bridge those
 * mutations into the global centered feedback surface and consume the old
 * inline result so users never see duplicate messages in arbitrary locations.
 *
 * Static/contextual notices can opt out with data-pp-no-popup="true".
 * Busy text (Saving…, Uploading…, etc.) intentionally remains inline.
 */
function installInlineFeedbackBridge() {
  if (inlineFeedbackBridgeInstalled || !document.documentElement) return;
  inlineFeedbackBridgeInstalled = true;
  ensureFeedbackStyles();

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

  // Catch feedback populated synchronously before the observer was installed.
  document.querySelectorAll(`.pp-alert,${LEGACY_FEEDBACK_SELECTOR}`).forEach((element) => {
    maybeBridgeInlineAlert(element);
    maybeBridgeLegacyFeedback(element);
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
