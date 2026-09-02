import { readFile } from "node:fs/promises";
import { test } from "node:test";
import assert from "node:assert/strict";

async function page(relativePath) {
  return readFile(
    new URL(`../frontend/${relativePath}`, import.meta.url),
    "utf8"
  );
}

test("login routes every supported role to its protected home", async () => {
  const html = await page("login.html");

  const routes = {
    parent: "./parent/students.html",
    teacher: "./teacher/scanner.html",
    school_admin: "./school-admin/dashboard.html",
    master_admin: "./master-admin/overview.html",
  };

  assert.match(
    html,
    /signInWithEmailAndPassword\(auth,\s*email,\s*password\)/
  );

  assert.match(
    html,
    /getIdTokenResult\(true\)/
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
