import {
  auth,
  db,
  showToast,
  setSubmitButtonBusy
} from "./firebase-init.js";
import {
  collection,
  query,
  where,
  orderBy,
  limit,
  onSnapshot,
  doc,
  updateDoc,
  writeBatch
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";
import {
  onAuthStateChanged
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";

const root = document.querySelector("[data-pp-notification-center]");

if (!root) {
  console.warn("PickupPass notification center mount not found.");
} else {
  const expectedRole = String(root.dataset.role || "").trim();
  const loginPath = String(root.dataset.loginPath || "../login.html").trim();

  let currentUid = "";
  let allNotifications = [];
  let activeFilter = "all";
  let unsubscribeNotifications = null;

  const ui = {
    unreadCount: document.getElementById("unreadCount"),
    totalCount: document.getElementById("totalCount"),
    markAll: document.getElementById("markAllReadBtn"),
    allFilter: document.getElementById("allFilterBtn"),
    unreadFilter: document.getElementById("unreadFilterBtn"),
    loading: document.getElementById("loadingState"),
    list: document.getElementById("notificationList"),
    empty: document.getElementById("emptyState"),
    emptyTitle: document.getElementById("emptyTitle"),
    emptyDetail: document.getElementById("emptyDetail"),
    error: document.getElementById("errorState"),
    errorDetail: document.getElementById("errorDetail"),
    retry: document.getElementById("retryBtn")
  };

  onAuthStateChanged(auth, async (user) => {
    if (!user) {
      stopNotificationListener();
      window.location.href = loginPath;
      return;
    }

    try {
      const tokenResult = await user.getIdTokenResult();
      if (expectedRole && tokenResult.claims.role !== expectedRole) {
        stopNotificationListener();
        window.location.href = loginPath;
        return;
      }
    } catch (_) {
      stopNotificationListener();
      window.location.href = loginPath;
      return;
    }

    if (currentUid === user.uid && unsubscribeNotifications) return;
    currentUid = user.uid;
    startNotificationListener();
  });

  ui.allFilter?.addEventListener("click", () => {
    activeFilter = "all";
    updateFilterButtons();
    render();
  });

  ui.unreadFilter?.addEventListener("click", () => {
    activeFilter = "unread";
    updateFilterButtons();
    render();
  });

  ui.retry?.addEventListener("click", startNotificationListener);
  window.addEventListener("pagehide", stopNotificationListener);

  function startNotificationListener() {
    if (!currentUid) return;

    stopNotificationListener();
    showLoading();

    const notificationQuery = query(
      collection(db, "notifications"),
      where("recipientUid", "==", currentUid),
      orderBy("createdAt", "desc"),
      limit(100)
    );

    unsubscribeNotifications = onSnapshot(
      notificationQuery,
      (snapshot) => {
        allNotifications = snapshot.docs.map((entry) => ({
          id: entry.id,
          ...entry.data()
        }));
        render();
      },
      (error) => {
        console.error("Failed to listen for role notifications", error);
        showError(error);
      }
    );
  }

  function stopNotificationListener() {
    unsubscribeNotifications?.();
    unsubscribeNotifications = null;
  }

  function render() {
    const unreadCount = allNotifications.filter((notification) => !notification.read).length;
    const visible = activeFilter === "unread"
      ? allNotifications.filter((notification) => !notification.read)
      : allNotifications;

    if (ui.unreadCount) ui.unreadCount.textContent = String(unreadCount);
    if (ui.totalCount) ui.totalCount.textContent = String(allNotifications.length);
    if (ui.unreadFilter) {
      ui.unreadFilter.textContent = unreadCount > 0 ? `Unread ${unreadCount}` : "Unread";
    }
    ui.markAll?.classList.toggle("hidden", unreadCount === 0);
    ui.loading?.classList.add("hidden");
    ui.error?.classList.add("hidden");

    if (!ui.list || !ui.empty) return;

    if (visible.length === 0) {
      ui.list.classList.add("hidden");
      ui.empty.classList.remove("hidden");

      if (ui.emptyTitle) {
        ui.emptyTitle.textContent = activeFilter === "unread"
          ? "You're all caught up"
          : "No notifications yet";
      }
      if (ui.emptyDetail) {
        ui.emptyDetail.textContent = activeFilter === "unread"
          ? "There are no unread updates that need your attention."
          : emptyInboxMessage();
      }
      return;
    }

    ui.empty.classList.add("hidden");
    ui.list.classList.remove("hidden");
    ui.list.innerHTML = "";

    visible.forEach((notification) => {
      ui.list.appendChild(createNotificationRow(notification));
    });
  }

  function createNotificationRow(notification) {
    const unread = !notification.read;
    const presentation = notificationPresentation(notification.type);
    const destination = notificationDestination(notification);
    const actionable = unread || Boolean(destination);
    const row = document.createElement(actionable ? "button" : "article");

    row.className = `pp-notification-row${
      unread ? " pp-notification-row--unread" : ""
    }${destination ? " pp-notification-row--actionable" : ""}`;

    if (actionable) {
      row.type = "button";
      row.setAttribute(
        "aria-label",
        destination
          ? `Open ${notification.title || presentation.label}`
          : `Mark ${notification.title || "notification"} as read`
      );
    }

    const sender = notification.senderName
      ? `<span class="pp-notification-sender">From ${escapeHtml(notification.senderName)}</span>`
      : "";
    const timestamp = notification.createdAt?.toDate
      ? formatTime(notification.createdAt.toDate())
      : "";

    row.innerHTML = `
      <div class="pp-notification-icon" aria-hidden="true">
        ${presentation.icon}
      </div>
      <div class="pp-notification-main">
        <div class="pp-notification-title-row">
          <h2>${escapeHtml(notification.title || presentation.label)}</h2>
          ${
            unread
              ? '<span class="pp-notification-unread-dot" aria-label="Unread"></span>'
              : destination
                ? '<span class="pp-notification-open" aria-hidden="true">›</span>'
                : ""
          }
        </div>
        <div class="pp-notification-meta">
          <span class="pp-notification-type">${escapeHtml(presentation.label)}</span>
          ${sender}
          ${timestamp ? `<time>${escapeHtml(timestamp)}</time>` : ""}
        </div>
        ${
          notification.body
            ? `<p class="pp-notification-body">${escapeHtml(notification.body)}</p>`
            : ""
        }
        ${destination ? '<p class="pp-notification-hint">Open related screen</p>' : ""}
      </div>
    `;

    if (actionable) {
      row.addEventListener("click", async () => {
        const marked = await markAsRead(notification);
        if (destination && marked) window.location.href = destination;
      });
    }

    return row;
  }

  async function markAsRead(notification) {
    if (notification.read) return true;

    notification.read = true;
    render();

    try {
      await updateDoc(
        doc(db, "notifications", notification.id),
        { read: true }
      );
      return true;
    } catch (_) {
      notification.read = false;
      render();
      showToast("Couldn't mark this notification as read.", "error");
      return false;
    }
  }

  ui.markAll?.addEventListener("click", async () => {
    const unread = allNotifications.filter((notification) => !notification.read);
    if (unread.length === 0 || ui.markAll.disabled) return;

    const unreadIds = new Set(unread.map((notification) => notification.id));
    unread.forEach((notification) => {
      notification.read = true;
    });
    render();

    setSubmitButtonBusy(ui.markAll, true, "Marking…");
    const batch = writeBatch(db);
    unread.forEach((notification) => {
      batch.update(doc(db, "notifications", notification.id), { read: true });
    });

    try {
      await batch.commit();
    } catch (_) {
      allNotifications.forEach((notification) => {
        if (unreadIds.has(notification.id)) notification.read = false;
      });
      render();
      showToast("Couldn't mark all notifications as read.", "error");
    } finally {
      setSubmitButtonBusy(ui.markAll, false);
    }
  });

  function notificationDestination(notification) {
    const explicit = [
      notification.destination,
      notification.destinationPath,
      notification.route,
      notification.path
    ].find((value) => typeof value === "string" && value.trim());

    if (explicit) {
      const safe = safeRoleDestination(explicit);
      if (safe) return safe;
    }

    const normalized = normalizeType(notification.type);

    if (expectedRole === "school_admin") {
      if (normalized.includes("launch")) {
        return "/school-admin/launch-readiness.html";
      }
      if (includesAny(normalized, ["billing", "payment", "invoice", "subscription"])) {
        return "/school-admin/billing.html";
      }
      if (includesAny(normalized, ["student", "guardian", "verification"])) {
        return "/school-admin/students-lifecycle.html";
      }
      if (includesAny(normalized, ["security", "session", "account", "device"])) {
        return "/account.html?return=school-admin/notifications.html";
      }
      if (includesAny(normalized, ["broadcast", "announcement"])) {
        return "/school-admin/dashboard.html";
      }
      return "";
    }

    if (expectedRole === "master_admin") {
      if (includesAny(normalized, ["demo_request", "demo", "inquiry", "lead"])) {
        return "/master-admin/demo-requests.html";
      }
      if (normalized.includes("launch")) {
        return masterSchoolDestination(notification.schoolId);
      }
      if (includesAny(normalized, ["billing", "payment", "invoice", "subscription"])) {
        return "/master-admin/billing.html";
      }
      if (includesAny(normalized, ["security", "session", "device"])) {
        return "/master-admin/operations.html#security";
      }
      if (includesAny(normalized, ["incident", "runtime", "health", "observability"])) {
        return "/master-admin/operations.html#runtime";
      }
      if (includesAny(normalized, ["school", "tenant"])) {
        return masterSchoolDestination(notification.schoolId);
      }
      if (normalized.includes("account")) {
        return "/account.html?return=master-admin/notifications.html";
      }
    }

    return "";
  }

  function masterSchoolDestination(schoolId) {
    const id = String(schoolId || "").trim();
    return id
      ? `/master-admin/index.html?launchReviewSchoolId=${encodeURIComponent(id)}`
      : "/master-admin/index.html";
  }

  function safeRoleDestination(value) {
    const raw = String(value || "").trim();
    if (!raw || /^https?:\/\//i.test(raw) || raw.startsWith("//")) return "";

    try {
      const url = new URL(raw, window.location.origin);
      if (url.origin !== window.location.origin) return "";

      const allowedPrefixes = expectedRole === "master_admin"
        ? ["/master-admin/"]
        : expectedRole === "school_admin"
          ? ["/school-admin/"]
          : [];

      const accountAllowed = url.pathname === "/account.html";
      if (!accountAllowed && !allowedPrefixes.some((prefix) => url.pathname.startsWith(prefix))) {
        return "";
      }

      return `${url.pathname}${url.search}${url.hash}`;
    } catch (_) {
      return "";
    }
  }

  function notificationPresentation(type) {
    const normalized = normalizeType(type);

    if (includesAny(normalized, ["demo_request", "demo", "inquiry", "lead"])) {
      return { label: "Demo inquiry", icon: campaignIcon() };
    }
    if (normalized.includes("launch")) {
      return { label: "Launch readiness", icon: rocketIcon() };
    }
    if (includesAny(normalized, ["billing", "payment", "invoice", "subscription"])) {
      return { label: "Billing", icon: receiptIcon() };
    }
    if (includesAny(normalized, ["security", "session", "device", "account"])) {
      return { label: "Security", icon: shieldIcon() };
    }
    if (includesAny(normalized, ["incident", "runtime", "health", "observability"])) {
      return { label: "Operations", icon: pulseIcon() };
    }
    if (includesAny(normalized, ["broadcast", "announcement"])) {
      return { label: "Announcement", icon: campaignIcon() };
    }
    if (includesAny(normalized, ["student", "guardian", "pickup", "release", "dismiss"])) {
      return { label: "School operations", icon: peopleIcon() };
    }
    return { label: "PickupPass update", icon: bellIcon() };
  }

  function emptyInboxMessage() {
    return expectedRole === "master_admin"
      ? "Launch review requests, demo inquiries, and platform alerts will appear here."
      : "Launch decisions and school administration updates will appear here.";
  }

  function updateFilterButtons() {
    ui.allFilter?.setAttribute("aria-pressed", String(activeFilter === "all"));
    ui.unreadFilter?.setAttribute("aria-pressed", String(activeFilter === "unread"));
  }

  function showLoading() {
    ui.loading?.classList.remove("hidden");
    ui.list?.classList.add("hidden");
    ui.empty?.classList.add("hidden");
    ui.error?.classList.add("hidden");
  }

  function showError(error) {
    ui.loading?.classList.add("hidden");
    ui.list?.classList.add("hidden");
    ui.empty?.classList.add("hidden");
    ui.error?.classList.remove("hidden");
    if (ui.errorDetail) {
      ui.errorDetail.textContent = error?.code === "permission-denied"
        ? "Your notification session is no longer authorized. Sign in again and complete account security verification."
        : "Check your connection and try again.";
    }
    if (ui.unreadCount) ui.unreadCount.textContent = "—";
    if (ui.totalCount) ui.totalCount.textContent = "—";
    ui.markAll?.classList.add("hidden");
  }

  function normalizeType(value) {
    return String(value || "").trim().toLowerCase();
  }

  function includesAny(value, needles) {
    return needles.some((needle) => value.includes(needle));
  }

  function formatTime(date) {
    if (!(date instanceof Date) || Number.isNaN(date.getTime())) return "";

    const deltaMs = Date.now() - date.getTime();
    const minutes = Math.floor(deltaMs / 60_000);
    if (minutes >= 0 && minutes < 1) return "Just now";
    if (minutes >= 1 && minutes < 60) return `${minutes}m ago`;

    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;

    return date.toLocaleString([], {
      month: "short",
      day: "numeric",
      year: date.getFullYear() === new Date().getFullYear() ? undefined : "numeric",
      hour: "numeric",
      minute: "2-digit"
    });
  }

  function escapeHtml(value) {
    const div = document.createElement("div");
    div.textContent = String(value ?? "");
    return div.innerHTML;
  }

  function svg(paths) {
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths}</svg>`;
  }

  function bellIcon() {
    return svg('<path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/>');
  }

  function rocketIcon() {
    return svg('<path d="M4 13c-1.5 1.2-2 3-2 5 2 0 3.8-.5 5-2"/><path d="M10 14 5 9c2.5-4.5 7-7 12.5-7 .3 5.5-2.5 10-7 12Z"/><circle cx="14" cy="7" r="1.5"/><path d="m9 15-1 5 5-1"/>');
  }

  function receiptIcon() {
    return svg('<path d="M6 2h12v20l-3-2-3 2-3-2-3 2V2Z"/><path d="M9 7h6M9 11h6M9 15h4"/>');
  }

  function shieldIcon() {
    return svg('<path d="M12 3 4 6v5c0 5 3.4 9 8 10 4.6-1 8-5 8-10V6l-8-3Z"/><path d="m9 12 2 2 4-4"/>');
  }

  function pulseIcon() {
    return svg('<path d="M3 12h4l2-5 4 10 2-5h6"/>');
  }

  function campaignIcon() {
    return svg('<path d="M3 11v2a1 1 0 0 0 1 1h2l4 4V6L6 10H4a1 1 0 0 0-1 1Z"/><path d="M14 8a4 4 0 0 1 0 8"/>');
  }

  function peopleIcon() {
    return svg('<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/>');
  }
}
