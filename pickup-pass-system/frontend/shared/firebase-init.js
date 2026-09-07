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
const feedbackBrandMarkUrl =
  new URL("../assets/pickuppass-mark.svg", import.meta.url).href;
const recentFeedback = new Map();
let feedbackSequence = 0;
let inlineFeedbackBridgeInstalled = false;

const feedbackMeta = {
  success: {
    kicker: "Action completed",
    title: "Success",
    actionLabel: "Done",
    icon: '<path d="M20 6 9 17l-5-5"/>',
  },
  warning: {
    kicker: "Review required",
    title: "Please review",
    actionLabel: "Got it",
    icon: '<path d="M10.3 2.9 1.8 17a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 2.9a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4"/><path d="M12 17h.01"/>',
  },
  error: {
    kicker: "Action not completed",
    title: "Something went wrong",
    actionLabel: "Close",
    icon: '<circle cx="12" cy="12" r="9"/><path d="m9 9 6 6M15 9l-6 6"/>',
  },
  info: {
    kicker: "PickupPass update",
    title: "Information",
    actionLabel: "Okay",
    icon: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5"/><path d="M12 8h.01"/>',
  },
};

function ensureFeedbackStyles() {
  if (document.getElementById(FEEDBACK_STYLE_ID)) return;

  const style = document.createElement("style");
  style.id = FEEDBACK_STYLE_ID;
  style.textContent = `
    #${FEEDBACK_REGION_ID} {
      position: fixed;
      z-index: 1400;
      inset: 0;
      display: grid;
      place-items: center;
      padding:
        max(20px, env(safe-area-inset-top))
        max(20px, env(safe-area-inset-right))
        max(20px, env(safe-area-inset-bottom))
        max(20px, env(safe-area-inset-left));
      background:
        radial-gradient(
          560px 340px at 50% 42%,
          rgb(70 82 199 / .11),
          transparent 72%
        ),
        rgb(10 16 34 / .66);
      backdrop-filter: blur(12px) saturate(.82);
      -webkit-backdrop-filter: blur(12px) saturate(.82);
      pointer-events: auto;
    }

    #${FEEDBACK_REGION_ID}:empty {
      display: none;
    }

    .pp-feedback-toast {
      --pp-feedback-accent: var(--primary, #4652C7);
      --pp-feedback-soft: var(--primary-container, #EEF0FF);
      position: relative;
      width: min(500px, calc(100vw - 32px));
      max-height: min(88vh, 660px);
      overflow: auto;
      border:
        1px solid
        color-mix(
          in srgb,
          var(--pp-feedback-accent) 16%,
          var(--border, #E1E5F0)
        );
      border-radius: 28px;
      background:
        radial-gradient(
          360px 170px at 100% 0%,
          color-mix(
            in srgb,
            var(--pp-feedback-accent) 8%,
            transparent
          ),
          transparent 72%
        ),
        var(--surface, #FFFFFF);
      color: var(--text, #12182F);
      box-shadow:
        0 42px 110px rgb(2 6 23 / .38),
        0 14px 36px rgb(2 6 23 / .18),
        inset 0 1px 0 rgb(255 255 255 / .7);
      opacity: 0;
      transform: translateY(14px) scale(.97);
      transition:
        opacity 180ms ease,
        transform 230ms cubic-bezier(.2,.78,.24,1);
      -webkit-font-smoothing: antialiased;
    }

    .pp-feedback-toast::before {
      content: "";
      position: absolute;
      inset: 0 0 auto;
      height: 4px;
      border-radius: 28px 28px 0 0;
      background:
        linear-gradient(
          90deg,
          #4652C7,
          var(--pp-feedback-accent),
          #6D5DFB
        );
      opacity: .95;
    }

    .pp-feedback-toast.is-visible {
      opacity: 1;
      transform: translateY(0) scale(1);
    }

    .pp-feedback-toast.is-leaving {
      opacity: 0;
      transform: translateY(7px) scale(.985);
    }

    .pp-feedback-toast--success {
      --pp-feedback-accent: var(--success, #0F8A78);
      --pp-feedback-soft: var(--success-container, #DDF7F1);
    }

    .pp-feedback-toast--warning {
      --pp-feedback-accent: var(--warning, #D97706);
      --pp-feedback-soft: var(--warning-container, #FEF3C7);
    }

    .pp-feedback-toast--error {
      --pp-feedback-accent: var(--danger, #DC3545);
      --pp-feedback-soft: var(--danger-container, #FDE8EA);
    }

    .pp-feedback-toast--info {
      --pp-feedback-accent: var(--primary, #4652C7);
      --pp-feedback-soft: var(--primary-container, #EEF0FF);
    }

    .pp-feedback-toast__brand {
      min-height: 64px;
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 18px 70px 16px 22px;
      border-bottom: 1px solid var(--border, #E1E5F0);
    }

    .pp-feedback-toast__brand-mark {
      width: 32px;
      height: 32px;
      flex: 0 0 32px;
      display: block;
      border-radius: 9px;
      box-shadow: 0 8px 18px rgb(70 82 199 / .18);
    }

    .pp-feedback-toast__brand-copy {
      min-width: 0;
      display: grid;
      line-height: 1.1;
    }

    .pp-feedback-toast__brand-copy strong {
      color: var(--text-strong, #12182F);
      font:
        800 .79rem/1.2
        var(--font-sans, Inter, system-ui, sans-serif);
      letter-spacing: -.015em;
    }

    .pp-feedback-toast__brand-copy span {
      margin-top: 3px;
      color: var(--text-subtle, #8790A5);
      font:
        700 .59rem/1.2
        var(--font-sans, Inter, system-ui, sans-serif);
      letter-spacing: .08em;
      text-transform: uppercase;
    }

    .pp-feedback-toast__kind {
      margin-left: auto;
      max-width: 150px;
      padding: 6px 9px;
      border:
        1px solid
        color-mix(
          in srgb,
          var(--pp-feedback-accent) 20%,
          var(--border, #E1E5F0)
        );
      border-radius: 999px;
      background:
        color-mix(
          in srgb,
          var(--pp-feedback-accent) 8%,
          var(--surface, #FFFFFF)
        );
      color: var(--pp-feedback-accent);
      font:
        800 .61rem/1
        var(--font-sans, Inter, system-ui, sans-serif);
      letter-spacing: .06em;
      text-transform: uppercase;
      white-space: nowrap;
    }

    .pp-feedback-toast__main {
      display: grid;
      grid-template-columns: 66px minmax(0, 1fr);
      gap: 18px;
      padding: 26px 26px 22px;
    }

    .pp-feedback-toast__icon {
      width: 62px;
      height: 62px;
      display: grid;
      place-items: center;
      border:
        1px solid
        color-mix(
          in srgb,
          var(--pp-feedback-accent) 15%,
          transparent
        );
      border-radius: 19px;
      color: var(--pp-feedback-accent);
      background:
        linear-gradient(
          145deg,
          color-mix(
            in srgb,
            var(--pp-feedback-accent) 13%,
            var(--surface, #FFFFFF)
          ),
          color-mix(
            in srgb,
            var(--pp-feedback-accent) 5%,
            var(--surface, #FFFFFF)
          )
        );
      box-shadow:
        inset 0 0 0 7px
        color-mix(
          in srgb,
          var(--pp-feedback-accent) 4%,
          transparent
        );
    }

    .pp-feedback-toast__icon svg {
      width: 30px;
      height: 30px;
      fill: none;
      stroke: currentColor;
      stroke-width: 2;
      stroke-linecap: round;
      stroke-linejoin: round;
    }

    .pp-feedback-toast__content {
      min-width: 0;
      align-self: center;
    }

    .pp-feedback-toast__title {
      margin: 0;
      color: var(--text-strong, #12182F);
      font:
        820 1.32rem/1.22
        var(--font-sans, Inter, system-ui, sans-serif);
      letter-spacing: -.034em;
    }

    .pp-feedback-toast__message {
      margin: 8px 0 0;
      color: var(--text-muted, #64748B);
      font:
        500 .91rem/1.58
        var(--font-sans, Inter, system-ui, sans-serif);
      overflow-wrap: anywhere;
    }

    .pp-feedback-toast__footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 18px;
      padding: 16px 22px 20px;
      border-top: 1px solid var(--border, #E1E5F0);
      background:
        color-mix(
          in srgb,
          var(--surface-variant, #F6F7FB) 62%,
          transparent
        );
    }

    .pp-feedback-toast__assurance {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      max-width: 245px;
      color: var(--text-subtle, #8790A5);
      font:
        650 .65rem/1.35
        var(--font-sans, Inter, system-ui, sans-serif);
    }

    .pp-feedback-toast__assurance svg {
      width: 15px;
      height: 15px;
      flex: 0 0 15px;
      fill: none;
      stroke: var(--primary, #4652C7);
      stroke-width: 1.8;
      stroke-linecap: round;
      stroke-linejoin: round;
    }

    .pp-feedback-toast__action {
      min-width: 94px;
      min-height: 42px;
      padding: 10px 16px;
      border: 0;
      border-radius: 13px;
      background:
        linear-gradient(
          135deg,
          color-mix(in srgb, var(--pp-feedback-accent) 88%, #4652C7),
          color-mix(in srgb, var(--pp-feedback-accent) 74%, #6D5DFB)
        );
      color: #FFFFFF;
      cursor: pointer;
      font:
        800 .78rem/1
        var(--font-sans, Inter, system-ui, sans-serif);
      box-shadow:
        0 10px 22px
        color-mix(
          in srgb,
          var(--pp-feedback-accent) 24%,
          transparent
        );
      transition:
        transform 140ms ease,
        box-shadow 140ms ease,
        filter 140ms ease;
    }

    .pp-feedback-toast__action:hover {
      transform: translateY(-1px);
      filter: saturate(1.06);
      box-shadow:
        0 13px 26px
        color-mix(
          in srgb,
          var(--pp-feedback-accent) 30%,
          transparent
        );
    }

    .pp-feedback-toast__action:focus-visible,
    .pp-feedback-toast__close:focus-visible {
      outline: 3px solid
        color-mix(
          in srgb,
          var(--pp-feedback-accent) 26%,
          transparent
        );
      outline-offset: 3px;
    }

    .pp-feedback-toast__close {
      position: absolute;
      z-index: 2;
      top: 14px;
      right: 14px;
      width: 36px;
      height: 36px;
      border: 1px solid var(--border, #E1E5F0);
      border-radius: 12px;
      display: grid;
      place-items: center;
      background: var(--surface-variant, #F6F7FB);
      color: var(--text-muted, #64748B);
      cursor: pointer;
      transition:
        transform 140ms ease,
        background 140ms ease,
        color 140ms ease;
    }

    .pp-feedback-toast__close:hover {
      background:
        color-mix(
          in srgb,
          var(--pp-feedback-accent) 7%,
          var(--surface-variant, #F6F7FB)
        );
      color: var(--text-strong, #12182F);
      transform: scale(1.03);
    }

    .pp-feedback-toast__close svg {
      width: 17px;
      height: 17px;
      fill: none;
      stroke: currentColor;
      stroke-width: 2;
      stroke-linecap: round;
    }

    /* Terminal action results from older screens are consumed after being
       bridged into the standardized feedback dialog. Busy/progress text stays
       inline so users still see immediate activity close to the control. */
    .${FEEDBACK_CONSUMED_CLASS} {
      display: none !important;
    }

    @media (max-width: 640px) {
      #${FEEDBACK_REGION_ID} {
        padding:
          max(14px, env(safe-area-inset-top))
          14px
          max(14px, env(safe-area-inset-bottom));
      }

      .pp-feedback-toast {
        width: min(100%, 460px);
        border-radius: 24px;
      }

      .pp-feedback-toast::before {
        border-radius: 24px 24px 0 0;
      }

      .pp-feedback-toast__brand {
        padding: 16px 58px 14px 18px;
      }

      .pp-feedback-toast__kind {
        display: none;
      }

      .pp-feedback-toast__main {
        grid-template-columns: 54px minmax(0, 1fr);
        gap: 14px;
        padding: 22px 18px 20px;
      }

      .pp-feedback-toast__icon {
        width: 52px;
        height: 52px;
        border-radius: 16px;
      }

      .pp-feedback-toast__icon svg {
        width: 25px;
        height: 25px;
      }

      .pp-feedback-toast__title {
        font-size: 1.16rem;
      }

      .pp-feedback-toast__message {
        font-size: .86rem;
      }

      .pp-feedback-toast__footer {
        align-items: stretch;
        flex-direction: column;
        padding: 14px 18px 18px;
      }

      .pp-feedback-toast__assurance {
        max-width: none;
      }

      .pp-feedback-toast__action {
        width: 100%;
      }
    }

    @media (prefers-reduced-motion: reduce) {
      .pp-feedback-toast,
      .pp-feedback-toast__action,
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
  const actionLabel = String(options.actionLabel || meta.actionLabel);
  const region = createFeedbackRegion();

  // A second terminal result supersedes the first instead of stacking cards.
  while (region.firstElementChild) {
    region.firstElementChild._ppCleanup?.();
    region.firstElementChild.remove();
  }

  const toast = document.createElement("section");
  const toastId = `pp-feedback-${++feedbackSequence}`;
  toast.id = toastId;
  toast.className =
    `pp-feedback-toast pp-feedback-toast--${normalizedType}`;
  toast.setAttribute(
    "role",
    normalizedType === "error" || normalizedType === "warning"
      ? "alertdialog"
      : "dialog"
  );
  toast.setAttribute("aria-modal", "true");
  toast.setAttribute("aria-labelledby", `${toastId}-title`);
  toast.setAttribute("aria-describedby", `${toastId}-message`);
  toast.tabIndex = -1;
  toast._ppRestoreFocus = document.activeElement;

  const close = document.createElement("button");
  close.type = "button";
  close.className = "pp-feedback-toast__close";
  close.setAttribute("aria-label", "Close message");
  close.innerHTML =
    '<svg viewBox="0 0 24 24"><path d="m7 7 10 10M17 7 7 17"/></svg>';
  close.addEventListener("click", () => dismissFeedback(toast));

  const brand = document.createElement("div");
  brand.className = "pp-feedback-toast__brand";

  const brandMark = document.createElement("img");
  brandMark.className = "pp-feedback-toast__brand-mark";
  brandMark.src = feedbackBrandMarkUrl;
  brandMark.alt = "";
  brandMark.setAttribute("aria-hidden", "true");

  const brandCopy = document.createElement("span");
  brandCopy.className = "pp-feedback-toast__brand-copy";

  const brandName = document.createElement("strong");
  brandName.textContent = "PickupPass";

  const brandContext = document.createElement("span");
  brandContext.textContent = "Secure action feedback";

  brandCopy.append(brandName, brandContext);

  const kind = document.createElement("span");
  kind.className = "pp-feedback-toast__kind";
  kind.textContent = meta.kicker;

  brand.append(brandMark, brandCopy, kind);

  const main = document.createElement("div");
  main.className = "pp-feedback-toast__main";

  const iconWrap = document.createElement("span");
  iconWrap.className = "pp-feedback-toast__icon";
  iconWrap.setAttribute("aria-hidden", "true");
  iconWrap.innerHTML =
    `<svg viewBox="0 0 24 24">${meta.icon}</svg>`;

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
  main.append(iconWrap, content);

  const footer = document.createElement("div");
  footer.className = "pp-feedback-toast__footer";

  const assurance = document.createElement("span");
  assurance.className = "pp-feedback-toast__assurance";
  assurance.innerHTML =
    '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3 5 6v5c0 4.1 2.8 7.5 7 8.8 4.2-1.3 7-4.7 7-8.8V6l-7-3Z"/><path d="m9 12 2 2 4-4"/></svg><span>PickupPass keeps action results clear and consistent.</span>';

  const action = document.createElement("button");
  action.type = "button";
  action.className = "pp-feedback-toast__action";
  action.textContent = actionLabel;
  action.addEventListener("click", () => {
    if (typeof options.onAction === "function") {
      options.onAction();
    }
    dismissFeedback(toast);
  });

  footer.append(assurance, action);
  toast.append(close, brand, main, footer);
  region.appendChild(toast);

  const focusable = [close, action];

  const onKeyDown = (event) => {
    if (event.key === "Escape") {
      event.preventDefault();
      dismissFeedback(toast);
      return;
    }

    if (event.key !== "Tab") return;

    const first = focusable[0];
    const last = focusable[focusable.length - 1];

    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  };

  document.addEventListener("keydown", onKeyDown);
  toast._ppCleanup = () =>
    document.removeEventListener("keydown", onKeyDown);

  requestAnimationFrame(() => {
    toast.classList.add("is-visible");
    action.focus({ preventScroll: true });
  });

  return toast;
}

// Semantic aliases for new code. Existing screens can keep importing showToast.
export const showFeedback = showToast;
export const showSuccess = (message, options = {}) =>
  showToast(message, "success", options);
export const showWarning = (message, options = {}) =>
  showToast(message, "warning", options);
export const showError = (message, options = {}) =>
  showToast(message, "error", options);
export const showInfo = (message, options = {}) =>
  showToast(message, "info", options);

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
  ".pp-profile-status",
  ".pp-inline-feedback",
  "[data-pp-feedback]",
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
  if (!element?.classList?.contains(FEEDBACK_CONSUMED_CLASS)) return;
  element.classList.remove(FEEDBACK_CONSUMED_CLASS);
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

  if (element.classList.contains(FEEDBACK_CONSUMED_CLASS)) return;

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
  if (
    /error/i.test(id) ||
    /(?:failed|failure|error|could not|couldn't|unable to|invalid|not available|denied)/i.test(message)
  ) return "error";

  if (
    /warning|attention|review required|couldn't be sent|could not be sent|not verified/i.test(message)
  ) return "warning";

  if (
    /success/i.test(id) ||
    /(?:saved|updated|registered|created|completed|sent|approved|released|removed|deleted|linked|enabled|disabled|revoked|confirmed|cancelled|canceled|submitted|requested|reconciled|refreshed)(?:\b|!)/i.test(message)
  ) return "success";
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

  const selector = `.pp-alert,${LEGACY_FEEDBACK_SELECTOR}`;
  const observedElements = new WeakSet();

  const inspect = (element) => {
    if (!element || element.closest?.(`#${FEEDBACK_REGION_ID}`)) return;

    const state = [
      element.hidden ? "1" : "0",
      element.classList.contains("hidden") ? "1" : "0",
      element.classList.contains(FEEDBACK_CONSUMED_CLASS) ? "1" : "0",
      element.textContent?.trim() || "",
    ].join("\u0000");

    if (element.dataset.ppObservedFeedbackState === state) return;
    element.dataset.ppObservedFeedbackState = state;

    maybeBridgeInlineAlert(element);
    maybeBridgeLegacyFeedback(element);

    element.dataset.ppObservedFeedbackState = [
      element.hidden ? "1" : "0",
      element.classList.contains("hidden") ? "1" : "0",
      element.classList.contains(FEEDBACK_CONSUMED_CLASS) ? "1" : "0",
      element.textContent?.trim() || "",
    ].join("\u0000");
  };

  // Observe only actual feedback/status elements. The previous implementation
  // watched class/text/child mutations across the entire document, which made
  // Tailwind CDN pages unnecessarily expensive in Firefox.
  const feedbackObserver = new MutationObserver((mutations) => {
    const targets = new Set();

    for (const mutation of mutations) {
      const element =
        mutation.target.nodeType === Node.ELEMENT_NODE
          ? mutation.target
          : mutation.target.parentElement;

      const feedbackElement = element?.closest?.(selector);
      if (feedbackElement) targets.add(feedbackElement);
    }

    targets.forEach(inspect);
  });

  const observeFeedbackElement = (element) => {
    if (!element || observedElements.has(element)) return;
    if (element.closest?.(`#${FEEDBACK_REGION_ID}`)) return;

    observedElements.add(element);
    feedbackObserver.observe(element, {
      subtree: true,
      childList: true,
      characterData: true,
      attributes: true,
      attributeFilter: ["class", "hidden"],
    });
    inspect(element);
  };

  const discoverFeedbackElements = (node) => {
    if (!node || node.nodeType !== Node.ELEMENT_NODE) return;
    const element = node;

    if (element.matches?.(selector)) {
      observeFeedbackElement(element);
    }

    element.querySelectorAll?.(selector).forEach(observeFeedbackElement);
  };

  // Existing feedback elements are registered once at startup.
  document.querySelectorAll(selector).forEach(observeFeedbackElement);

  // Dynamic dialogs/forms may add new status nodes later. Observe only
  // child additions for discovery; do not monitor page-wide class or text
  // mutations.
  const discoveryRoot = document.body || document.documentElement;
  const discoveryObserver = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      mutation.addedNodes.forEach(discoverFeedbackElements);
    }
  });

  discoveryObserver.observe(discoveryRoot, {
    subtree: true,
    childList: true,
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
