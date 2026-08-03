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
// the app via localhost/127.0.0.1 (any local static server � Live Server,
// `python -m http.server`, `firebase serve`, etc.), it points at your
// local backend. Anywhere else (the deployed Firebase Hosting URL), it
// points at the deployed Cloud Run backend. This removes the single
// biggest local-dev footgun with the old approach: forgetting to switch
// the hardcoded URL back to the deployed one before running `firebase
// deploy`, which would silently ship a build that only works on your own
// machine.
//
// If your local backend runs on a different port, change LOCAL_API_BASE_URL only.
const LOCAL_API_BASE_URL = "http://localhost:8080/api";
const DEPLOYED_API_BASE_URL = "https://pickup-pass-backend-445244473897.asia-southeast1.run.app/api";

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
  return fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${idToken}`,
      ...(options.headers || {}),
    },
  });
}

/**
 * Consistent, non-blocking feedback for every app screen.  Unlike alert(),
 * this keeps the user in context, and unlike inline form text it stays
 * visible even when the action button is below the fold.
 */
export function showToast(message, type = "success") {
  // Canonical brand palette (matches shared/theme.css + Android Color.kt):
  // green for success, amber for caution, red for error, cool gray for info.
  const colors = {
    success: "bg-green-600",
    warning: "bg-amber-500",
    error: "bg-red-600",
    info: "bg-gray-800",
  };
  let region = document.getElementById("pickupPassToastRegion");
  if (!region) {
    region = document.createElement("div");
    region.id = "pickupPassToastRegion";
    region.className = "fixed inset-x-4 top-4 z-[100] mx-auto flex max-w-md flex-col gap-2 pointer-events-none";
    region.setAttribute("aria-live", "polite");
    document.body.appendChild(region);
  }

  const toast = document.createElement("div");
  toast.className = `${colors[type] || colors.info} pointer-events-auto rounded-xl px-4 py-3 text-sm font-medium text-white shadow-lg transition duration-200`;
  toast.setAttribute("role", type === "error" ? "alert" : "status");
  toast.textContent = message;
  region.appendChild(toast);

  window.setTimeout(() => {
    toast.classList.add("opacity-0", "-translate-y-1");
    window.setTimeout(() => toast.remove(), 200);
  }, type === "error" ? 6000 : 4500);
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
