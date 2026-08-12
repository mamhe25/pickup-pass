import { auth } from './firebase-init.js';
import { onAuthStateChanged, signOut } from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js';
import { mountThemeToggle, enhancePortal } from './shell.js';

const ITEMS = [
  { key:'schools', label:'Schools', href:'./index.html', icon:iconSchool },
  { key:'billing', label:'Billing', href:'./billing.html', icon:iconReceipt },
  { key:'operations', label:'Operations', href:'./operations.html', icon:iconShield }
];

function render(mount) {
  const active = mount.dataset.active || '';
  mount.innerHTML = `
    <header class="pp-appbar">
      <div class="pp-appbar__inner">
        <a class="pp-brandmark" href="./index.html" aria-label="PickupPass platform console">
          <span class="pp-brandmark__badge">${iconShield(true)}</span>
          <span class="flex flex-col"><span class="pp-brandmark__name">PickupPass</span><span class="pp-brandmark__tag">Platform Owner</span></span>
        </a>
        <div class="pp-shell-actions">
          <span id="masterEmail" class="text-xs text-ink-subtle hidden sm:inline"></span>
          <button data-pp-theme-toggle class="pp-icon-btn" type="button"></button>
          <button id="masterSignOut" class="pp-btn pp-btn--ghost" type="button">Sign out</button>
        </div>
      </div>
      <nav class="pp-navrow" aria-label="Platform administration">
        ${ITEMS.map(item => `<a class="pp-navlink" href="${item.href}" ${item.key===active?'aria-current="page"':''}>${item.icon()}<span class="pp-navlink__label">${item.label}</span></a>`).join('')}
      </nav>
    </header>`;
  mountThemeToggle(mount.querySelector('[data-pp-theme-toggle]'));
  enhancePortal();
  mount.querySelector('#masterSignOut').onclick = async () => { await signOut(auth); location.href='../login.html'; };
  onAuthStateChanged(auth, async user => {
    if (!user) { location.href='../login.html'; return; }
    mount.querySelector('#masterEmail').textContent = user.email || '';
    try {
      const token = await user.getIdTokenResult(true);
      if (token.claims.role !== 'master_admin') location.href='../login.html';
    } catch (_) { location.href='../login.html'; }
  });
}
function svg(paths){return `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths}</svg>`;}
function iconSchool(){return svg('<path d="M3 21h18"/><path d="M5 21V8l7-4 7 4v13"/><path d="M9 21v-5h6v5"/><path d="M9 10h.01M15 10h.01"/>');}
function iconReceipt(){return svg('<path d="M6 2h12v20l-3-2-3 2-3-2-3 2V2Z"/><path d="M9 7h6M9 11h6M9 15h4"/>');}
function iconShield(filled=false){if(filled)return '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 2 4 5v6c0 4.4 3.1 8.4 8 9.6 4.9-1.2 8-5.2 8-9.6V5l-8-3Z" fill="white" fill-opacity=".2"/><path d="m9.5 12.2 1.8 1.8 3.5-3.7" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';return svg('<path d="M12 3 4 6v5c0 5 3.4 9 8 10 4.6-1 8-5 8-10V6l-8-3Z"/><path d="m9 12 2 2 4-4"/>');}

const mount = document.getElementById('masterNav');
if (mount) render(mount);
