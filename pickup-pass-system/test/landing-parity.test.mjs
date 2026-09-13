import { readFile } from "node:fs/promises";
import { test } from "node:test";
import assert from "node:assert/strict";

async function frontend(relativePath) {
  return readFile(new URL(`../frontend/${relativePath}`, import.meta.url), "utf8");
}

test("public landing page uses the PickupPass indigo brand system", async () => {
  const [html, css] = await Promise.all([
    frontend("index.html"),
    frontend("landing.css"),
  ]);

  assert.match(html, /landing\.css/);
  assert.match(css, /--landing-midnight:\s*#171D46/i);
  assert.match(css, /--landing-violet:\s*#6D5DFB/i);
  assert.match(css, /var\(--brand-gradient\)/);

  assert.doesNotMatch(css, /--landing-evergreen/);
  assert.doesNotMatch(css, /--landing-lime/);
  assert.doesNotMatch(css, /#052e2b|#022c22|#064e3b|#065f46|#84cc16|#bef264|#a7f3d0|#d1fae5|#ecfdf5/i);
  assert.doesNotMatch(css, /rgba\(132\s*,\s*204\s*,\s*22/i);

  assert.match(
    css,
    /\.landing-security\s*\{[\s\S]*?linear-gradient\(145deg,var\(--landing-midnight-deep\),var\(--landing-midnight\)/
  );
  assert.match(
    css,
    /\.landing-demo-card\s*\{[\s\S]*?linear-gradient\(145deg,var\(--landing-midnight-deep\),var\(--landing-midnight\)/
  );
  assert.match(
    css,
    /\.landing-footer\s*\{[\s\S]*?var\(--landing-midnight-deep\)/
  );

  // Green remains acceptable only for actual verified/success semantics.
  assert.match(css, /\.landing-check\s*\{[\s\S]*?background:\s*var\(--success\)/);
  assert.match(css, /\.landing-verify-card__release strong\s*\{\s*color:\s*var\(--success\)/);
});
