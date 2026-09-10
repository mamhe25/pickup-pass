const DIGITS = 6;

function normalize(value) {
  return String(value ?? '').replace(/\D/g, '').slice(0, DIGITS);
}

function enhance(input) {
  if (!(input instanceof HTMLInputElement) || input.dataset.ppTotpReady === 'true') return;

  input.dataset.ppTotpReady = 'true';
  input.classList.add('pp-totp-field__input');
  input.setAttribute('inputmode', 'numeric');
  input.setAttribute('autocomplete', 'one-time-code');
  input.setAttribute('maxlength', String(DIGITS));
  input.setAttribute('pattern', '[0-9]{6}');

  const wrapper = document.createElement('div');
  wrapper.className = 'pp-totp-field';
  wrapper.setAttribute('role', 'group');
  wrapper.setAttribute('aria-label', input.getAttribute('aria-label') || '6-digit authenticator code');

  const slots = Array.from({ length: DIGITS }, (_, index) => {
    const slot = document.createElement('span');
    slot.className = 'pp-totp-field__slot';
    slot.dataset.index = String(index);
    slot.setAttribute('aria-hidden', 'true');
    return slot;
  });

  input.parentNode.insertBefore(wrapper, input);
  wrapper.append(input, ...slots);

  let lastSubmitted = '';

  const sync = () => {
    const value = normalize(input.value);
    if (value !== input.value) input.value = value;

    const activeIndex = Math.min(value.length, DIGITS - 1);
    slots.forEach((slot, index) => {
      slot.textContent = value[index] || '';
      slot.classList.toggle('is-active', index === activeIndex && value.length < DIGITS);
    });

    wrapper.classList.toggle('is-complete', value.length === DIGITS);
    wrapper.setAttribute('aria-invalid', input.getAttribute('aria-invalid') === 'true' ? 'true' : 'false');

    if (value.length < DIGITS) {
      lastSubmitted = '';
      return;
    }

    if (value === lastSubmitted) return;
    lastSubmitted = value;

    const target = input.dataset.ppAutoSubmit || '';
    if (!target) return;

    queueMicrotask(() => {
      if (normalize(input.value).length !== DIGITS) return;

      const [kind, id] = target.split(':', 2);
      const element = id ? document.getElementById(id) : null;
      if (!element) return;

      if (kind === 'form' && element instanceof HTMLFormElement) {
        element.requestSubmit();
      } else if (kind === 'button' && element instanceof HTMLElement) {
        element.click();
      }
    });
  };

  input.addEventListener('input', sync);
  input.addEventListener('change', sync);
  input.addEventListener('focus', () => {
    wrapper.classList.add('is-focused');
    sync();
  });
  input.addEventListener('blur', () => wrapper.classList.remove('is-focused'));
  input.addEventListener('invalid', () => {
    input.setAttribute('aria-invalid', 'true');
    sync();
  });

  wrapper.addEventListener('click', () => input.focus());

  const errorObserver = new MutationObserver(() => {
    const errorId = input.getAttribute('aria-describedby');
    if (!errorId) return;
    const error = document.getElementById(errorId);
    const visibleError = error && !error.classList.contains('hidden') && String(error.textContent || '').trim();
    input.setAttribute('aria-invalid', visibleError ? 'true' : 'false');
    sync();
  });

  const errorId = input.getAttribute('aria-describedby');
  const error = errorId ? document.getElementById(errorId) : null;
  if (error) errorObserver.observe(error, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });

  sync();
}

function boot() {
  document.querySelectorAll('input[data-pp-totp]').forEach(enhance);
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', boot, { once: true });
} else {
  boot();
}

export { enhance as enhanceTotpField };
