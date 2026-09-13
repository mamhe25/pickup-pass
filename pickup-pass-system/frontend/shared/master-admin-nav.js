import { auth } from './firebase-init.js';
import {
  onAuthStateChanged
} from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js';
import { mountThemeToggle, enhancePortal } from './shell.js';
import { mountAccountLink } from './account-link.js';
import { listenUnreadNotifications } from './notification-badge.js';

const ITEMS = [
  { key: 'overview', label: 'Overview', href: './overview.html', icon: iconDashboard },
  { key: 'schools', label: 'Schools', href: './index.html', icon: iconSchool },
  { key: 'inquiries', label: 'Inquiries', href: './demo-requests.html', icon: iconChat },
  { key: 'billing', label: 'Billing', href: './billing.html', icon: iconReceipt },
  { key: 'operations', label: 'Operations', href: './operations.html', icon: iconShield }
];

let unsubscribeUnread = null;
let unreadUid = '';

function render(mount) {
  ensureNotificationStyles();
  const active = mount.dataset.active || '';
  const notificationsCurrent = location.pathname.endsWith('/master-admin/notifications.html')
    ? ' aria-current="page"'
    : '';

  mount.innerHTML = `
    <header class="pp-appbar">
      <div class="pp-appbar__inner">
        <a class="pp-brandmark" href="./overview.html" aria-label="PickupPass platform control center">
          <span class="pp-brandmark__badge"><img src="../assets/pickuppass-mark.svg" alt="" /></span>
          <span class="flex flex-col">
            <span class="pp-brandmark__name"><span class="pp-wordmark__pickup">Pickup</span><span class="pp-wordmark__pass">Pass</span></span>
            <span class="pp-brandmark__tag">Platform Owner</span>
          </span>
        </a>

        <div class="pp-shell-actions">
          <span id="masterEmail" class="text-xs text-ink-subtle hidden sm:inline"></span>
          <a
            id="masterNotificationAction"
            class="pp-icon-btn pp-notification-action"
            href="./notifications.html"
            aria-label="Notifications"${notificationsCurrent}>
            ${iconBell()}
            <span
              id="masterUnreadBadge"
              class="pp-notification-action__badge hidden"
              aria-label="Unread notifications"></span>
          </a>
          <button data-pp-theme-toggle class="pp-icon-btn" type="button"></button>
        </div>
      </div>

      <nav class="pp-navrow" aria-label="Platform administration">
        ${ITEMS.map(item => `
          <a class="pp-navlink" href="${item.href}" ${item.key === active ? 'aria-current="page"' : ''}>
            ${item.icon()}
            <span class="pp-navlink__label">${item.label}</span>
          </a>
        `).join('')}
      </nav>
    </header>
  `;

  mountThemeToggle(mount.querySelector('[data-pp-theme-toggle]'));
  mountAccountLink(mount);
  enhancePortal();
  wireLaunchReviewDeepLink();

  onAuthStateChanged(auth, async user => {
    if (!user) {
      stopUnreadListener();
      location.href = '../login.html';
      return;
    }

    const email = mount.querySelector('#masterEmail');
    if (email) email.textContent = user.email || '';

    try {
      const token = await user.getIdTokenResult(true);
      if (token.claims.role !== 'master_admin') {
        stopUnreadListener();
        location.href = '../login.html';
        return;
      }
      startUnreadListener(user.uid, mount.querySelector('#masterUnreadBadge'));
    } catch (_) {
      stopUnreadListener();
      location.href = '../login.html';
    }
  });
}

function startUnreadListener(uid, badge) {
  if (!uid || !badge) return;
  if (unsubscribeUnread && unreadUid === uid) return;

  stopUnreadListener();
  unreadUid = uid;
  unsubscribeUnread = listenUnreadNotifications(uid, badge);
}

function stopUnreadListener() {
  unsubscribeUnread?.();
  unsubscribeUnread = null;
  unreadUid = '';
}

function ensureNotificationStyles() {
  if (document.querySelector('link[data-pp-notification-center]')) return;
  const link = document.createElement('link');
  link.rel = 'stylesheet';
  link.href = '../shared/notification-center.css';
  link.dataset.ppNotificationCenter = 'true';
  document.head.appendChild(link);
}

function wireLaunchReviewDeepLink() {
  if (!location.pathname.endsWith('/master-admin/index.html')) return;

  const params = new URLSearchParams(location.search);
  const schoolId = String(params.get('launchReviewSchoolId') || '').trim();
  if (!schoolId) return;

  let observer = null;
  let timeoutId = null;
  let handled = false;

  const openReview = () => {
    if (handled) return true;

    const manageButton = [...document.querySelectorAll('button.manage[data-id]')]
      .find(button => button.dataset.id === schoolId);
    if (!manageButton) return false;

    handled = true;
    observer?.disconnect();
    if (timeoutId) window.clearTimeout(timeoutId);

    manageButton.click();
    requestAnimationFrame(() => {
      document.getElementById('launchReviewBtn')?.focus({ preventScroll: true });
    });

    const cleanUrl = new URL(location.href);
    cleanUrl.searchParams.delete('launchReviewSchoolId');
    history.replaceState(
      history.state,
      '',
      `${cleanUrl.pathname}${cleanUrl.search}${cleanUrl.hash}`
    );
    return true;
  };

  if (openReview()) return;

  observer = new MutationObserver(openReview);
  observer.observe(document.body, { childList: true, subtree: true });
  timeoutId = window.setTimeout(() => observer?.disconnect(), 10_000);
}

window.addEventListener('pagehide', stopUnreadListener);

function svg(paths) {
  return `
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      ${paths}
    </svg>
  `;
}

function iconDashboard() {
  return svg(
    '<rect x="3" y="3" width="7" height="7" rx="1"/>' +
    '<rect x="14" y="3" width="7" height="7" rx="1"/>' +
    '<rect x="3" y="14" width="7" height="7" rx="1"/>' +
    '<rect x="14" y="14" width="7" height="7" rx="1"/>'
  );
}

function iconSchool() {
  return svg(
    '<path d="M3 21h18"/>' +
    '<path d="M5 21V8l7-4 7 4v13"/>' +
    '<path d="M9 21v-5h6v5"/>' +
    '<path d="M9 10h.01M15 10h.01"/>'
  );
}

function iconChat() {
  return svg(
    '<path d="M21 15a4 4 0 0 1-4 4H9l-6 3v-6a4 4 0 0 1-1-2.7V7a4 4 0 0 1 4-4h11a4 4 0 0 1 4 4v8Z"/>' +
    '<path d="M7 8h10M7 12h7"/>'
  );
}

function iconReceipt() {
  return svg(
    '<path d="M6 2h12v20l-3-2-3 2-3-2-3 2V2Z"/>' +
    '<path d="M9 7h6M9 11h6M9 15h4"/>'
  );
}

function iconShield() {
  return svg(
    '<path d="M12 3 4 6v5c0 5 3.4 9 8 10 4.6-1 8-5 8-10V6l-8-3Z"/>' +
    '<path d="m9 12 2 2 4-4"/>'
  );
}

function iconBell() {
  return svg(
    '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/>' +
    '<path d="M13.7 21a2 2 0 0 1-3.4 0"/>'
  );
}

const mount = document.getElementById('masterNav');
if (mount) render(mount);
