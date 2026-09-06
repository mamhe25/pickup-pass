import { auth, authedFetch } from './firebase-init.js';
import { onAuthStateChanged } from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js';

export function mountAccountLink(mount) {
  const nav = mount.querySelector('nav');
  if (!nav || nav.querySelector('[data-account-link]')) return;
  const link = document.createElement('a');
  link.href = '../account.html';
  link.className = 'pp-navlink';
  link.dataset.accountLink = '';
  link.textContent = 'Account settings';
  nav.appendChild(link);
  // Reconcile the profile on return after Firebase completes email verification.
  onAuthStateChanged(auth, user => {
    if (user) authedFetch('/session/me').catch(() => {});
  });
}
