import { auth, authedFetch, showToast } from './firebase-init.js';
import { onAuthStateChanged } from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js';

export function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;').replaceAll("'", '&#039;');
}

export function fmtDate(value) {
  if (!value) return '—';
  try { return new Date(value).toLocaleString(); } catch (_) { return String(value); }
}

export function fmtMoney(amountMinor, currency='PHP') {
  const n = Number(amountMinor || 0) / 100;
  try { return new Intl.NumberFormat('en-PH', { style:'currency', currency: currency || 'PHP' }).format(n); }
  catch (_) { return `${currency || 'PHP'} ${n.toFixed(2)}`; }
}

export function titleCase(value) {
  return String(value ?? '').replaceAll('_',' ').replace(/\b\w/g, m => m.toUpperCase());
}

export function statusPill(value) {
  const raw = String(value ?? 'unknown');
  const key = raw.toLowerCase();
  const tone = /active|healthy|approved|paid|verified|up|ready|current/.test(key) ? 'success'
    : /pending|trial|attention|warning|near|review/.test(key) ? 'warning'
    : /suspend|cancel|overdue|risk|error|failed|critical|rejected|past due|past_due/.test(key) ? 'danger' : 'neutral';
  return `<span class="pp-status pp-status--${tone}">${escapeHtml(titleCase(raw))}</span>`;
}

export function setText(id, value) {
  const el = document.getElementById(id); if (el) el.textContent = value ?? '';
}

export function setHtml(id, value) {
  const el = document.getElementById(id); if (el) el.innerHTML = value ?? '';
}

export function loadingRows(cols, text='Loading…') {
  return `<tr><td colspan="${cols}" class="pp-empty">${escapeHtml(text)}</td></tr>`;
}

export function emptyRows(cols, text='No records found.') {
  return `<tr><td colspan="${cols}" class="pp-empty">${escapeHtml(text)}</td></tr>`;
}

export async function apiJson(path, options={}) {
  const res = await authedFetch(path, options);
  const contentType = res.headers.get('content-type') || '';
  const data = contentType.includes('application/json') ? await res.json().catch(() => ({})) : await res.text();
  if (!res.ok) {
    const msg = typeof data === 'object' ? (data.error || data.reason || data.message) : data;
    const err = new Error(msg || `Request failed (HTTP ${res.status})`);
    err.status = res.status; err.payload = data; throw err;
  }
  return data;
}

export async function downloadAuthed(path, fallbackName='download.bin') {
  const res = await authedFetch(path, { method:'GET', headers:{ Accept:'*/*' } });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.error || `Download failed (HTTP ${res.status})`);
  }
  const blob = await res.blob();
  const cd = res.headers.get('content-disposition') || '';
  const match = cd.match(/filename\*?=(?:UTF-8''|\")?([^\";]+)/i);
  const name = match ? decodeURIComponent(match[1].replace(/^"|"$/g,'')) : fallbackName;
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a'); a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 3000);
}

export function requireRole(roles, onReady) {
  const allowed = Array.isArray(roles) ? roles : [roles];
  return onAuthStateChanged(auth, async user => {
    if (!user) { window.location.href = '../login.html'; return; }
    try {
      const token = await user.getIdTokenResult(true);
      const role = token.claims.role;
      if (!allowed.includes(role)) {
        showToast('This account does not have access to this page.', 'error');
        setTimeout(() => window.location.href = '../login.html', 800);
        return;
      }
      if (onReady) await onReady(user, token.claims);
    } catch (err) {
      showToast(err.message || 'Could not verify your session.', 'error');
    }
  });
}

export function bindRefresh(buttonId, fn) {
  const btn = document.getElementById(buttonId);
  if (!btn) return;
  btn.addEventListener('click', async () => {
    if (btn.disabled) return;
    const old = btn.textContent; btn.disabled = true; btn.textContent = 'Refreshing…';
    try { await fn(); } catch (err) { showToast(err.message || 'Refresh failed.', 'error'); }
    finally { btn.disabled = false; btn.textContent = old; }
  });
}

export function confirmAction(message) { return window.confirm(message); }
