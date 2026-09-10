import { auth, authedFetch } from './firebase-init.js';
import { onAuthStateChanged } from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js';

export function mountAccountLink(mount) {
  const nav = mount.querySelector('nav');
  if (!nav || nav.querySelector('[data-account-link]')) return;
  const link = document.createElement('a');
  link.href = '../account.html';
  link.className = 'pp-navlink';
  link.dataset.accountLink = '';
  link.setAttribute('aria-label', 'Account security');
  link.innerHTML =
    '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="8" r="4"/><path d="M5 21v-1a7 7 0 0 1 14 0v1"/><path d="M17 4.5 19 6l2-1.5v3L19 9l-2-1.5v-3Z"/></svg><span class="pp-navlink__label">Account security</span>';
  nav.appendChild(link);

  onAuthStateChanged(auth, user => {
    if (user) authedFetch('/session/me').catch(() => {});
  });
}
