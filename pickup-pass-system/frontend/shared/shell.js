const THEME_KEY = 'pp.theme';

export function applyThemePreference() {
  let pref = 'system';
  try { pref = localStorage.getItem(THEME_KEY) || 'system'; } catch (_) {}
  if (pref === 'light' || pref === 'dark') document.documentElement.dataset.theme = pref;
  else delete document.documentElement.dataset.theme;
  return pref;
}

function iconFor(pref) {
  if (pref === 'dark') return '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8Z"/></svg>';
  if (pref === 'light') return '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/></svg>';
  return '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4" width="18" height="12" rx="2"/><path d="M8 20h8M12 16v4"/></svg>';
}

export function mountThemeToggle(button) {
  if (!button) return;
  let pref = applyThemePreference();
  const render = () => {
    button.innerHTML = iconFor(pref);
    button.title = `Theme: ${pref}. Click to change.`;
    button.setAttribute('aria-label', `Theme is ${pref}. Change theme.`);
  };
  button.addEventListener('click', () => {
    pref = pref === 'system' ? 'light' : pref === 'light' ? 'dark' : 'system';
    try { localStorage.setItem(THEME_KEY, pref); } catch (_) {}
    applyThemePreference();
    render();
  });
  render();
}

export function enhancePortal() {
  document.body.classList.add('pp-portal-body');
  applyThemePreference();
  const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  if (reduce || !('IntersectionObserver' in window)) return;
  const items = [...document.querySelectorAll('.pp-page-head,.pp-section,.pp-kpi,.pp-card')].slice(0, 80);
  const io = new IntersectionObserver(entries => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue;
      entry.target.classList.add('pp-revealed');
      io.unobserve(entry.target);
    }
  }, { threshold: .06, rootMargin: '0px 0px -18px 0px' });
  items.forEach((el, index) => {
    el.classList.add('pp-reveal');
    el.style.transitionDelay = `${Math.min(index, 8) * 22}ms`;
    io.observe(el);
  });
}

applyThemePreference();
