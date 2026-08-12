#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');
const os = require('os');
const cp = require('child_process');

const root = path.resolve(__dirname, '..');
const system = path.join(root, 'pickup-pass-system');
const frontend = path.join(system, 'frontend');
let pass = 0;
let fail = 0;

function ok(label) { console.log(`PASS  ${label}`); pass++; }
function bad(label, detail='') { console.error(`FAIL  ${label}${detail ? ` — ${detail}` : ''}`); fail++; }
function text(p) { return fs.readFileSync(p, 'utf8'); }
function exists(rel) {
  const p = path.join(root, rel);
  if (fs.existsSync(p)) ok(`exists: ${rel}`); else bad(`exists: ${rel}`);
}
function walk(dir, ext) {
  const out=[];
  for (const e of fs.readdirSync(dir, {withFileTypes:true})) {
    const p=path.join(dir,e.name);
    if (e.isDirectory()) out.push(...walk(p,ext));
    else if (!ext || p.endsWith(ext)) out.push(p);
  }
  return out;
}

[
  'pickup-pass-system/frontend/shared/portal.css',
  'pickup-pass-system/frontend/shared/shell.js',
  'pickup-pass-system/frontend/shared/portal.js',
  'pickup-pass-system/frontend/assets/default-avatar.svg',
  'pickup-pass-system/frontend/parent/devices.html',
  'pickup-pass-system/frontend/parent/manage-guardians.html',
  'pickup-pass-system/frontend/teacher/operations.html',
  'pickup-pass-system/frontend/school-admin/dashboard.html',
  'pickup-pass-system/frontend/school-admin/pickup-settings.html',
  'pickup-pass-system/frontend/school-admin/billing.html',
  'pickup-pass-system/frontend/school-admin/launch-readiness.html',
  'pickup-pass-system/frontend/master-admin/index.html',
  'pickup-pass-system/frontend/master-admin/billing.html',
  'pickup-pass-system/frontend/master-admin/operations.html',
].forEach(exists);

try {
  const cfg = JSON.parse(text(path.join(system, 'firebase.json')));
  const rule = (cfg.hosting?.rewrites || []).find(r => r.source === '/api/**');
  if (rule?.run?.serviceId === 'pickup-pass-backend' && rule?.run?.region === 'asia-southeast1') {
    ok('Firebase Hosting /api/** rewrites to pickup-pass-backend in asia-southeast1');
  } else bad('Firebase Hosting Cloud Run rewrite is current');
} catch (e) { bad('firebase.json parses', e.message); }

const init = text(path.join(frontend, 'shared', 'firebase-init.js'));
if (/const DEPLOYED_API_BASE_URL = ["']\/api["'];/.test(init)) ok('deployed web API uses same-origin /api');
else bad('deployed web API uses same-origin /api');

const html = walk(frontend, '.html');
const roleHtml = html.filter(p => /[\\/](parent|teacher|school-admin|master-admin)[\\/]/.test(p));
let shellProblem = [];
for (const p of roleHtml) {
  const s = text(p);
  if (!s.includes('../shared/portal.css') || !s.includes('pp-portal-body')) shellProblem.push(path.relative(frontend,p));
}
if (!shellProblem.length) ok(`modern portal shell applied to ${roleHtml.length} signed-in role screens`);
else bad('modern portal shell applied to every role screen', shellProblem.join(', '));

let hardcoded = [];
for (const p of [...html, ...walk(frontend,'.js')]) {
  if (/https:\/\/pickup-pass-backend-[^\s"']*\.run\.app/i.test(text(p))) hardcoded.push(path.relative(frontend,p));
}
if (!hardcoded.length) ok('no hard-coded Cloud Run production URL remains in web source');
else bad('no hard-coded Cloud Run production URL remains in web source', hardcoded.join(', '));

// JavaScript syntax: standalone files + inline scripts. Remote src scripts are skipped.
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'pickuppass-u16-'));
let syntaxErrors=[];
for (const p of walk(frontend,'.js')) {
  const r=cp.spawnSync(process.execPath,['--check',p],{encoding:'utf8'});
  if (r.status !== 0) syntaxErrors.push(`${path.relative(frontend,p)}: ${r.stderr.trim()}`);
}
const scriptRe=/<script\b([^>]*)>([\s\S]*?)<\/script>/gi;
for (const p of html) {
  const s=text(p); let m; let i=0;
  while ((m=scriptRe.exec(s))) {
    i++;
    if (/\bsrc\s*=/.test(m[1]) || !m[2].trim()) continue;
    const isModule=/\btype\s*=\s*["']module["']/i.test(m[1]);
    const tmp=path.join(temp,`inline-${Math.random().toString(36).slice(2)}${isModule?'.mjs':'.js'}`);
    fs.writeFileSync(tmp,m[2]);
    const r=cp.spawnSync(process.execPath,['--check',tmp],{encoding:'utf8'});
    if (r.status !== 0) syntaxErrors.push(`${path.relative(frontend,p)} inline script ${i}: ${r.stderr.trim()}`);
  }
}
fs.rmSync(temp,{recursive:true,force:true});
if (!syntaxErrors.length) ok('all standalone and inline web JavaScript passes node --check');
else bad('web JavaScript syntax', syntaxErrors.slice(0,5).join('\n'));

const firebaseNested=path.join(system,'firebase','firebase.json');
if (fs.existsSync(firebaseNested)) {
  try {
    const a=JSON.parse(text(path.join(system,'firebase.json')));
    const b=JSON.parse(text(firebaseNested));
    if (JSON.stringify(a.hosting)===JSON.stringify(b.hosting)) ok('root and nested Firebase Hosting configs are aligned');
    else bad('root and nested Firebase Hosting configs are aligned');
  } catch(e) { bad('nested firebase.json parses', e.message); }
}

console.log(`\nPickupPass Phase 3 Update 16 web verification: ${pass} passed, ${fail} failed.`);
process.exit(fail ? 1 : 0);
