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
  const [html, mfa, legacyCompletionCss] = await Promise.all([
    page("login.html"),
    shared("mfa.js"),
    shared("premium-completion.css"),
  ]);

  assert.doesNotMatch(
    html,
    /cdn\.tailwindcss\.com|tailwind-config\.js/,
    "login must not load the Tailwind browser compiler"
  );

  assert.doesNotMatch(
    html,
    /premium-completion\.css/,
    "login must not load the retired legacy completion override"
  );

  assert.doesNotMatch(
    legacyCompletionCss,
    /\.pp-auth-story\s*\{|#064E3B|#065F46|#047857|rgba\(16,\s*185,\s*129/,
    "legacy completion CSS must not reintroduce the old green auth brand"
  );

  const portalCss = await shared("portal.css");
  assert.match(
    portalCss,
    /\.pp-auth-input\s*\{[\s\S]*?box-sizing:\s*border-box/,
    "login inputs must stay within the auth form column"
  );
  assert.match(
    portalCss,
    /\.pp-auth-page\s*\{[\s\S]*?height:\s*100svh[\s\S]*?overflow:\s*hidden/,
    "login page must remain viewport-contained"
  );

  assert.match(
    portalCss,
    /\.pp-auth-input:autofill\s*\{[\s\S]*?1000px[\s\S]*?--pp-auth-input-bg/,
    "saved credentials must retain the branded auth input surface"
  );
  assert.match(
    portalCss,
    /\.pp-auth-input:-webkit-autofill/,
    "Chromium/WebKit autofill must retain the branded auth input surface"
  );

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

    assert.doesNotMatch(
      html,
      /pp-backlink|← My Students/,
      "student pickup pass should not duplicate My Students navigation"
    );

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
  "web action feedback is centralized, premium, accessible, and bridges legacy results",
  async () => {
    const js = await shared("firebase-init.js");

    assert.match(js, /export function showToast\(message, type = "success", options = \{\}\)/);
    assert.match(js, /export const showFeedback = showToast/);
    assert.match(js, /export const showSuccess/);
    assert.match(js, /export const showWarning/);
    assert.match(js, /export const showError/);
    assert.match(js, /export const showInfo/);

    assert.match(js, /feedbackBrandMarkUrl/);
    assert.match(js, /pp-feedback-toast__brand/);
    assert.match(js, /pp-feedback-toast__brand-mark/);
    assert.match(js, /pp-feedback-toast__kind/);
    assert.match(js, /pp-feedback-toast__main/);
    assert.match(js, /pp-feedback-toast__footer/);
    assert.match(js, /pp-feedback-toast__assurance/);
    assert.match(js, /pp-feedback-toast__action/);
    assert.match(js, /Secure action feedback/);

    assert.match(js, /prefers-reduced-motion/);
    assert.match(js, /normalizedType === "error" \|\| normalizedType === "warning"/);
    assert.match(js, /"aria-modal", "true"/);
    assert.match(js, /"aria-labelledby"/);
    assert.match(js, /"aria-describedby"/);
    assert.match(js, /action\.focus\(\{ preventScroll: true \}\)/);
    assert.match(js, /event\.key === "Escape"/);
    assert.match(js, /event\.key !== "Tab"/);
    assert.match(js, /RECENT_FEEDBACK_WINDOW_MS/);

    assert.match(js, /installInlineFeedbackBridge\(\)/);
    assert.match(js, /\.pp-alert/);
    assert.match(js, /LEGACY_FEEDBACK_SELECTOR/);
    assert.match(js, /\.pp-profile-status/);
    assert.match(js, /\[data-pp-feedback\]/);
    assert.match(js, /data-pp-no-popup/);
    assert.match(js, /feedbackObserver\.observe\(element/);
    assert.match(js, /discoveryObserver\.observe\(discoveryRoot/);
    assert.doesNotMatch(js, /observer\.observe\(document\.documentElement/);
    assert.match(js, /ppObservedFeedbackState/);
    assert.match(js, /classList\.contains\(FEEDBACK_CONSUMED_CLASS\)\) return/);
  }
);

test("parent guardian navigation is student-scoped and account security lives under profile", async () => {
  const [nav, students, overview, manager, profile, account] = await Promise.all([
    shared("parent-nav.js"),
    page("parent/students.html"),
    page("parent/guardians.html"),
    page("parent/manage-guardians.html"),
    page("parent/profile.html"),
    shared("account.js"),
  ]);

  assert.doesNotMatch(nav, /label:\s*"Guardians"/);
  assert.doesNotMatch(nav, /label:\s*"Devices"/);
  assert.doesNotMatch(nav, /mountAccountLink/);
  assert.match(students, /Manage guardians/);
  assert.match(overview, /location\.replace\("\.\/students\.html"\)/);
  assert.match(manager, /Managing guardian access for/);
  assert.doesNotMatch(manager, /pp-backlink|← My Students/);
  assert.match(manager, /data-active="students"/);
  assert.match(profile, /\.\.\/account\.html\?return=parent\/profile\.html/);
  assert.match(profile, /Manage security/);
  assert.match(profile, /id="devices"/);
  assert.match(profile, /\/session\/devices/);
  assert.match(profile, /Sign out other devices/);
  assert.doesNotMatch(profile, /href="\.\/devices\.html"/);
  assert.match(account, /new Set\(\["parent\/profile\.html"\]\)/);
  assert.match(account, /Back to My profile/);
});


test("legacy parent devices route redirects into My Profile", async () => {
  const devices = await page("parent/devices.html");
  assert.match(devices, /profile\.html#devices/);
  assert.match(devices, /location\.replace\("\.\/profile\.html#devices"\)/);
});


test("parent profile remains responsive and premium across account and security surfaces", async () => {
  const [html, css, accountLink] = await Promise.all([
    page("parent/profile.html"),
    page("parent/profile.css"),
    shared("account-link.js"),
  ]);

  assert.match(html, /pp-profile-card__heading/);
  assert.match(html, /pp-profile-security__eyebrow/);
  assert.match(html, /Manage security/);
  assert.doesNotMatch(html, /pp-profile-next/);

  assert.match(css, /max-width:\s*1040px/);
  assert.match(css, /rgba\(109, 93, 251, \.34\)/);
  assert.doesNotMatch(css, /rgba\(132, 204, 22/);
  assert.match(css, /\.pp-profile-security\s*\{[\s\S]*?grid-template-columns:\s*50px minmax\(0,1fr\) auto/);
  assert.match(css, /@media \(max-width: 720px\)[\s\S]*?\.pp-profile-security__actions[\s\S]*?grid-column:\s*1 \/ -1/);
  assert.doesNotMatch(css, /margin-left:\s*54px/);
  assert.match(css, /\.pp-profile-grid\s*\{[\s\S]*?align-items:\s*stretch/);
  assert.match(css, /\.pp-profile-card\s*\{[\s\S]*?height:\s*100%/);
  assert.match(css, /\.pp-profile-card--photo\s*\{[\s\S]*?display:\s*flex/);
  assert.match(css, /\.pp-profile-card--photo \.pp-profile-photo-wrap\s*\{[\s\S]*?flex:\s*1 1 auto/);

  assert.match(accountLink, /aria-label', 'Account security'/);
  assert.match(accountLink, /pp-navlink__label">Account security/);
  assert.doesNotMatch(accountLink, /Account settings/);
});


test("account security keeps readable theme contrast across light and dark modes", async () => {
  const css = await shared("account.css");

  assert.match(css, /body\s*\{[\s\S]*?var\(--bg\)[\s\S]*?color:\s*var\(--text\)/);
  assert.match(css, /\.account-card\s*\{[\s\S]*?var\(--surface\)/);
  assert.match(css, /\.account-identity\s*\{[\s\S]*?var\(--surface\)/);
  assert.match(css, /input\s*\{[\s\S]*?var\(--surface\)[\s\S]*?var\(--text\)/);
  assert.match(css, /fieldset:disabled\s*\{[\s\S]*?opacity:\s*1/);
  assert.match(css, /fieldset:disabled input\s*\{[\s\S]*?-webkit-text-fill-color:\s*var\(--text-muted\)/);
  assert.match(css, /\.account-brand \.pp-wordmark__pickup\s*\{[\s\S]*?var\(--text-strong\)/);
  assert.match(css, /:root\[data-theme="dark"\] \.account-card/);
  assert.doesNotMatch(css, /background:\s*rgba\(255,255,255,\.(?:88|94|72)\)/);
});
