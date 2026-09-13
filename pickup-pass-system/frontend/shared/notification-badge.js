import { db } from "./firebase-init.js";
import {
  collection,
  query,
  where,
  onSnapshot
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

/**
 * Bind a recipient-scoped real-time unread-count badge.
 *
 * Firestore rules remain authoritative: a user can only read notification
 * documents whose recipientUid matches their authenticated uid.
 */
export function listenUnreadNotifications(uid, badge) {
  if (!uid || !badge) return () => {};

  const unreadQuery = query(
    collection(db, "notifications"),
    where("recipientUid", "==", uid),
    where("read", "==", false)
  );

  return onSnapshot(
    unreadQuery,
    (snapshot) => {
      const count = snapshot.size;
      badge.textContent = count > 9 ? "9+" : count > 0 ? String(count) : "";
      badge.setAttribute(
        "aria-label",
        count > 0
          ? `${count} unread notification${count === 1 ? "" : "s"}`
          : "No unread notifications"
      );
      badge.classList.toggle("hidden", count === 0);
    },
    () => {
      badge.textContent = "";
      badge.setAttribute("aria-label", "Unread notification count unavailable");
      badge.classList.add("hidden");
    }
  );
}
