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

test("login routes every supported role to its protected home", async () => {
  const [html, mfa] = await Promise.all([
    page("login.html"),
    shared("mfa.js"),
  ]);

  const routes = {
    parent: "./parent/students.html",
    teacher: "./teacher/scanner.html",
    school_admin: "./school-admin/dashboard.html",
    master_admin: "./master-admin/overview.html",
  };

  assert.match(
    html,
    /signInWithEmailAndPassword\(\s*auth,\s*email,\s*password\s*\)/
  );

  assert.match(
    html,
    /authContext\(\s*user,\s*true\s*\)/
  );

  assert.match(
    mfa,
    /authContext\(user, forceRefresh = true\)/
  );

  assert.match(
    mfa,
    /user\.getIdTokenResult\(forceRefresh\)/
  );

  for (const [role, route] of Object.entries(routes)) {
    const escapedRoute = route.replace(
      /[.*+?^${}()|[\]\\]/g,
      "\\$&"
    );

    assert.match(
      html,
      new RegExp(
        `case\\s+["']${role}["'][\\s\\S]*?${escapedRoute}`
      )
    );
  }
});

test(
  "parent pass journey uses selected student and renders expiring signed QR",
  async () => {
    const html = await page("parent/pickup-pass.html");

    assert.match(
      html,
      /params\.get\(\s*["']studentId["']\s*\)/
    );

    assert.match(
      html,
      /authedFetch\s*\(\s*["']\/parent\/generate-token["']/
    );

    assert.match(
      html,
      /method:\s*["']POST["']/
    );

    assert.match(
      html,
      /JSON\.stringify\(\{\s*studentId\s*\}\)/
    );

    assert.match(
      html,
      /text:\s*qrToken/
    );

    assert.match(
      html,
      /new Date\(\s*expiresAt\s*\)/
    );

    assert.match(
      html,
      /startCountdown\(\s*expiry\s*,\s*version\s*\)/
    );

    assert.match(
      html,
      /version\s*!==\s*generationVersion/
    );
  }
);

test(
  "teacher scanner verifies before approval and keeps retry idempotency",
  async () => {
    const html = await page("teacher/scanner.html");

    const verifyIndex =
      html.indexOf('authedFetch("/pickup/verify"');

    const approveIndex =
      html.indexOf('authedFetch("/pickup/approve"');

    assert.ok(
      verifyIndex >= 0,
      "scanner must call verify endpoint"
    );

    assert.ok(
      approveIndex > verifyIndex,
      "approval must happen only after verification"
    );

    assert.match(
      html,
      /currentApprovalKey\s*=\s*crypto\.randomUUID\(\)/
    );

    assert.match(
      html,
      /["']Idempotency-Key["']:\s*currentApprovalKey/
    );

    assert.match(
      html,
      /JSON\.stringify\(\{\s*qrToken:\s*currentQrToken\s*\}\)/
    );

    assert.match(
      html,
      /result\.status\s*!==\s*["']release_approved["']/
    );

    assert.match(
      html,
      /currentApprovalKey\s*=\s*null/
    );
  }
);

test(
  "parent notifications remain recipient-scoped and permit read-only updates",
  async () => {
    const html = await page("parent/notifications.html");

    assert.match(
      html,
      /where\(["']recipientUid["'],\s*["']==["'],\s*currentUid\)/
    );

    assert.match(
      html,
      /orderBy\(["']createdAt["'],\s*["']desc["']\)/
    );

    assert.match(
      html,
      /updateDoc\([\s\S]*?\{\s*read:\s*true\s*\}\)/
    );

    assert.match(
      html,
      /batch\.update\([\s\S]*?\{\s*read:\s*true\s*\}\)/
    );
  }
);

test(
  "web action feedback is centralized, accessible, dismissible, and bridges legacy alerts",
  async () => {
    const js = await shared("firebase-init.js");

    assert.match(js, /export function showToast\(message, type = "success", options = \{\}\)/);
    assert.match(js, /export const showFeedback = showToast/);
    assert.match(js, /pp-feedback-toast__close/);
    assert.match(js, /prefers-reduced-motion/);
    assert.match(js, /role", normalizedType === "error" \? "alertdialog" : "dialog"/);
    assert.match(js, /"aria-modal", "true"/);
    assert.match(js, /"aria-labelledby"/);
    assert.match(js, /"aria-describedby"/);
    assert.match(js, /close\.focus\(\{ preventScroll: true \}\)/);
    assert.match(js, /event\.key === "Escape"/);
    assert.match(js, /RECENT_FEEDBACK_WINDOW_MS/);
    assert.match(js, /installInlineFeedbackBridge\(\)/);
    assert.match(js, /\.pp-alert/);
    assert.match(js, /LEGACY_FEEDBACK_SELECTOR/);
    assert.match(js, /data-pp-no-popup/);
    assert.match(js, /feedbackObserver\.observe\(element/);
    assert.match(js, /discoveryObserver\.observe\(discoveryRoot/);
    assert.doesNotMatch(js, /observer\.observe\(document\.documentElement/);
  }
);
