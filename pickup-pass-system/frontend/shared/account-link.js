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
    '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3 4 6v5c0 5 3.4 9 8 10 4.6-1 8-5 8-10V6l-8-3Z"/><path d="m9 12 2 2 4-4"/></svg><span class="pp-navlink__label">Account security</span>';
  nav.appendChild(link);
  // Reconcile the profile on return after Firebase completes email verification.
  onAuthStateChanged(auth, user => {
    if (user) authedFetch('/session/me').catch(() => {});
  });
}
