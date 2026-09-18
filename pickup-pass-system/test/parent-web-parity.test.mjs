import { readFile } from "node:fs/promises";
import { test } from "node:test";
import assert from "node:assert/strict";

async function page(relativePath) {
  return readFile(
    new URL(`../frontend/${relativePath}`, import.meta.url),
    "utf8"
  );
}

async function shared(relativePath) {
  return readFile(
    new URL(`../frontend/shared/${relativePath}`, import.meta.url),
    "utf8"
  );
}

test("parent notification inbox and nav stay realtime", async () => {
  const [notifications, nav] = await Promise.all([
    page("parent/notifications.html"),
    shared("parent-nav.js"),
  ]);

  assert.match(notifications, /\bonSnapshot\b/);
  assert.match(notifications, /unsubscribeNotifications\s*=\s*onSnapshot/);
  assert.match(notifications, /pagehide["'],\s*stopNotificationListener/);
  assert.match(notifications, /notificationDestination\(notification\)/);
  assert.doesNotMatch(notifications, /\bgetDocs\s*\(/);

  assert.match(nav, /listenUnreadNotifications/);
  assert.match(nav, /id="parentNotificationAction"/);
  assert.match(nav, /id="parentUnreadBadge"/);
  assert.match(nav, /unsubscribeUnread\s*=\s*listenUnreadNotifications\(uid, badge\)/);
  assert.doesNotMatch(nav, /\bonSnapshot\b/);
  assert.doesNotMatch(nav, /key:\s*"notifications"/);
  assert.match(nav, /pagehide["'],\s*stopUnreadListener/);
});

test("account credential changes use focused dialogs instead of open page forms", async () => {
  const [html, css, accountJs] = await Promise.all([
    page("account.html"),
    shared("account-focus.css"),
    shared("account.js"),
  ]);

  assert.match(html, /class="account-action-grid"/);
  assert.match(html, /id="openEmailDialog"/);
  assert.match(html, /id="openPasswordDialog"/);
  assert.match(html, /<dialog id="emailDialog"[\s\S]*?<form id="emailForm">/);
  assert.match(html, /<dialog id="passwordDialog"[\s\S]*?<form id="passwordForm">/);
  assert.match(html, /id="accountSignOutDialog"/);
  assert.doesNotMatch(html, /class="account-credential-grid"/);

  assert.match(css, /\.account-action-grid\s*\{/);
  assert.match(css, /\.account-dialog\s*\{/);
  assert.match(css, /\.account-confirm-card\s*\{/);
  assert.match(css, /@media \(max-width:\s*560px\)/);

  assert.match(accountJs, /const forms = \[\.\.\.document\.querySelectorAll\("form"\)\]/);
  assert.match(accountJs, /form\.id === "emailForm"/);
  assert.match(accountJs, /actions\.changePassword/);
});

test("parent keeps guardians and devices inside their consolidated destinations", async () => {
  const [devices, guardians, profile, nav] = await Promise.all([
    page("parent/devices.html"),
    page("parent/guardians.html"),
    page("parent/profile.html"),
    shared("parent-nav.js"),
  ]);

  assert.match(devices, /profile\.html#devices/);
  assert.match(guardians, /location\.replace\("\.\/students\.html"\)/);
  assert.match(profile, /id="devices"/);
  assert.match(profile, /Sign out other devices/);
  assert.doesNotMatch(nav, /label:\s*"Devices"/);
  assert.doesNotMatch(nav, /label:\s*"Guardians"/);
});
