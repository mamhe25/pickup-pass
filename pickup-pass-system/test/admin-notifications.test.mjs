import { readFile } from "node:fs/promises";
import { test } from "node:test";
import assert from "node:assert/strict";

async function page(relativePath) {
  return readFile(new URL(`../frontend/${relativePath}`, import.meta.url), "utf8");
}

async function shared(relativePath) {
  return readFile(new URL(`../frontend/shared/${relativePath}`, import.meta.url), "utf8");
}

test("school admin and platform owner notification bells stay live and recipient scoped", async () => {
  const [adminNav, ownerNav, badge, center, adminPage, ownerPage] = await Promise.all([
    shared("school-admin-nav.js"),
    shared("master-admin-nav.js"),
    shared("notification-badge.js"),
    shared("notification-center.js"),
    page("school-admin/notifications.html"),
    page("master-admin/notifications.html"),
  ]);

  assert.match(adminNav, /href="\/school-admin\/notifications\.html"/);
  assert.match(adminNav, /listenUnreadNotifications\(uid, badge\)/);
  assert.match(adminNav, /id="adminUnreadBadge"/);
  assert.match(adminNav, /tokenResult\.claims\.role !== "school_admin"/);

  assert.match(ownerNav, /href="\.\/notifications\.html"/);
  assert.match(ownerNav, /listenUnreadNotifications\(uid, badge\)/);
  assert.match(ownerNav, /id="masterUnreadBadge"/);
  assert.match(ownerNav, /token\.claims\.role !== 'master_admin'/);

  assert.match(badge, /where\("recipientUid",\s*"==",\s*uid\)/);
  assert.match(badge, /where\("read",\s*"==",\s*false\)/);
  assert.match(badge, /onSnapshot\(/);

  assert.match(center, /where\("recipientUid",\s*"==",\s*currentUid\)/);
  assert.match(center, /orderBy\("createdAt",\s*"desc"\)/);
  assert.match(center, /updateDoc\([\s\S]*?\{ read: true \}/);
  assert.match(center, /batch\.update\([\s\S]*?\{ read: true \}/);
  assert.match(center, /url\.origin !== window\.location\.origin/);

  assert.match(adminPage, /data-role="school_admin"/);
  assert.match(adminPage, /notification-center\.js/);
  assert.match(ownerPage, /data-role="master_admin"/);
  assert.match(ownerPage, /notification-center\.js/);
});

test("admin notification destinations remain role scoped and launch updates deep link correctly", async () => {
  const [center, ownerNav] = await Promise.all([
    shared("notification-center.js"),
    shared("master-admin-nav.js"),
  ]);

  assert.match(center, /return "\/school-admin\/launch-readiness\.html"/);
  assert.match(center, /\/master-admin\/index\.html\?launchReviewSchoolId=/);
  assert.match(center, /allowedPrefixes = expectedRole === "master_admin"/);
  assert.match(center, /\["\/master-admin\/"\]/);
  assert.match(center, /\["\/school-admin\/"\]/);

  assert.match(ownerNav, /params\.get\('launchReviewSchoolId'\)/);
  assert.match(ownerNav, /button\.dataset\.id === schoolId/);
  assert.match(ownerNav, /manageButton\.click\(\)/);
  assert.match(ownerNav, /getElementById\('launchReviewBtn'\)\?\.focus/);
  assert.match(ownerNav, /searchParams\.delete\('launchReviewSchoolId'\)/);
  assert.doesNotMatch(ownerNav, /launchReviewBtn'\)\?\.click/);
});
