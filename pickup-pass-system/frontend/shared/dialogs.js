let activeDialog = null;
let sequence = 0;

function ensureDialog() {
  if (activeDialog && document.contains(activeDialog.dialog)) return activeDialog;

  const dialog = document.createElement('dialog');
  dialog.className = 'pp-action-dialog';
  dialog.innerHTML = `
    <form method="dialog" data-pp-action-form>
      <div class="pp-action-dialog__head">
        <p class="pp-action-dialog__eyebrow">PickupPass action</p>
        <h2 data-pp-action-title>Confirm action</h2>
      </div>
      <div class="pp-action-dialog__body">
        <p class="pp-action-dialog__message" data-pp-action-message></p>
        <div class="pp-action-dialog__fields" data-pp-action-fields></div>
      </div>
      <div class="pp-action-dialog__footer">
        <button type="submit" value="cancel" class="pp-btn pp-btn--secondary" data-pp-action-cancel>Cancel</button>
        <button type="submit" value="confirm" class="pp-btn pp-btn--primary pp-action-dialog__confirm" data-pp-action-confirm>Continue</button>
      </div>
    </form>
  `;
  document.body.appendChild(dialog);

  activeDialog = {
    dialog,
    form: dialog.querySelector('[data-pp-action-form]'),
    title: dialog.querySelector('[data-pp-action-title]'),
    message: dialog.querySelector('[data-pp-action-message]'),
    fields: dialog.querySelector('[data-pp-action-fields]'),
    cancel: dialog.querySelector('[data-pp-action-cancel]'),
    confirm: dialog.querySelector('[data-pp-action-confirm]')
  };

  dialog.addEventListener('click', event => {
    if (event.target !== dialog) return;
    const rect = dialog.getBoundingClientRect();
    const outside =
      event.clientX < rect.left || event.clientX > rect.right ||
      event.clientY < rect.top || event.clientY > rect.bottom;
    if (outside) dialog.close('cancel');
  });

  return activeDialog;
}

function fieldMarkup(field, index) {
  const id = `pp-action-field-${++sequence}-${index}`;
  const required = field.required ? ' required' : '';
  const minLength = Number.isFinite(field.minLength) ? ` minlength="${field.minLength}"` : '';
  const maxLength = Number.isFinite(field.maxLength) ? ` maxlength="${field.maxLength}"` : '';
  const placeholder = field.placeholder ? ` placeholder="${escapeAttribute(field.placeholder)}"` : '';
  const value = field.value != null ? ` value="${escapeAttribute(field.value)}"` : '';
  const autocomplete = field.autocomplete ? ` autocomplete="${escapeAttribute(field.autocomplete)}"` : ' autocomplete="off"';
  const inputMode = field.inputmode ? ` inputmode="${escapeAttribute(field.inputmode)}"` : '';
  const label = escapeHtml(field.label || field.name || 'Value');
  const name = escapeAttribute(field.name || `field${index}`);

  if (field.type === 'textarea') {
    return `
      <div class="pp-action-dialog__field">
        <label for="${id}">${label}</label>
        <textarea id="${id}" name="${name}" class="pp-field"${required}${minLength}${maxLength}${placeholder}>${escapeHtml(field.value || '')}</textarea>
      </div>
    `;
  }

  return `
    <div class="pp-action-dialog__field">
      <label for="${id}">${label}</label>
      <input id="${id}" name="${name}" type="${escapeAttribute(field.type || 'text')}" class="pp-field"${required}${minLength}${maxLength}${placeholder}${value}${autocomplete}${inputMode}>
    </div>
  `;
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function escapeAttribute(value) {
  return escapeHtml(value);
}

export function fieldsDialog({
  title = 'Confirm action',
  message = '',
  confirmLabel = 'Continue',
  cancelLabel = 'Cancel',
  danger = false,
  fields = []
} = {}) {
  const ui = ensureDialog();

  if (ui.dialog.open) ui.dialog.close('cancel');

  ui.title.textContent = title;
  ui.message.textContent = message;
  ui.message.classList.toggle('hidden', !message);
  ui.cancel.textContent = cancelLabel;
  ui.confirm.textContent = confirmLabel;
  ui.confirm.classList.toggle('is-danger', danger);
  ui.confirm.classList.toggle('pp-btn--primary', !danger);
  ui.confirm.classList.toggle('pp-btn--danger', danger);
  ui.fields.innerHTML = fields.map(fieldMarkup).join('');

  return new Promise(resolve => {
    const onClose = () => {
      ui.dialog.removeEventListener('close', onClose);
      if (ui.dialog.returnValue !== 'confirm') {
        resolve(null);
        return;
      }

      const data = new FormData(ui.form);
      const result = {};
      fields.forEach((field, index) => {
        result[field.name || `field${index}`] = String(data.get(field.name || `field${index}`) ?? '').trim();
      });
      resolve(result);
    };

    ui.dialog.addEventListener('close', onClose);
    ui.dialog.showModal();
    requestAnimationFrame(() => {
      const firstField = ui.fields.querySelector('input, textarea, select');
      (firstField || ui.confirm).focus();
    });
  });
}

export async function confirmDialog(message, options = {}) {
  const result = await fieldsDialog({
    title: options.title || 'Confirm action',
    message,
    confirmLabel: options.confirmLabel || 'Continue',
    cancelLabel: options.cancelLabel || 'Cancel',
    danger: !!options.danger,
    fields: []
  });
  return result !== null;
}

export async function promptDialog({
  title = 'Provide details',
  message = '',
  label = 'Details',
  value = '',
  placeholder = '',
  required = false,
  minLength,
  maxLength,
  multiline = false,
  confirmLabel = 'Continue',
  cancelLabel = 'Cancel',
  danger = false
} = {}) {
  const result = await fieldsDialog({
    title,
    message,
    confirmLabel,
    cancelLabel,
    danger,
    fields: [{
      name: 'value',
      label,
      value,
      placeholder,
      required,
      minLength,
      maxLength,
      type: multiline ? 'textarea' : 'text'
    }]
  });
  return result ? result.value : null;
}
