import { auth, authedFetch } from './firebase-init.js';
import * as sdk from 'https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js';
import { createAccountActions, accountError } from './account-actions.js';

const actions = createAccountActions(sdk);
const forms = [...document.querySelectorAll('form')];
const homes = { parent: 'parent/students.html', teacher: 'teacher/scanner.html', school_admin: 'school-admin/dashboard.html', master_admin: 'master-admin/overview.html' };
let ready = false;
let busy = false;
const lock = () => forms.forEach(form => form.querySelector('fieldset').disabled = !ready || busy);
sdk.onAuthStateChanged(auth, async user => {
  ready = false;
  lock();
  if (!user) { location.replace('./login.html'); return; }
  try {
    const token = await user.getIdTokenResult();
    const home = homes[token.claims.role];
    if (!home) throw new Error('This account does not have access to PickupPass.');
    document.getElementById('backLink').href = './' + home;
    document.getElementById('currentEmail').textContent = user.email || '';
    ready = true;
    lock();
    const response = await authedFetch('/session/me');
    if (!response.ok) throw new Error('Could not refresh your profile. Please sign in again if your email has changed.');
  } catch (error) {
    document.getElementById('accountStatus').textContent = accountError(error);
  }
});

for (const form of forms) form.addEventListener('submit', async event => {
  event.preventDefault();
  if (!ready || busy || !form.reportValidity()) return;
  busy = true;
  lock();
  const status = form.querySelector('.form-status');
  status.dataset.error = 'false';
  status.textContent = 'Please wait…';
  try {
    if (form.id === 'emailForm') {
      const email = await actions.changeEmail(auth.currentUser, document.getElementById('emailPassword').value, document.getElementById('newEmail').value);
      status.textContent = `Verification email sent to ${email}. Check your inbox and spam folder, verify the link, then sign in with your new email.`;
    } else {
      await actions.changePassword(auth.currentUser, document.getElementById('currentPassword').value, document.getElementById('newPassword').value, document.getElementById('confirmPassword').value);
      status.textContent = 'Your password has been changed.';
    }
    form.reset();
  } catch (error) {
    status.dataset.error = 'true';
    status.textContent = accountError(error);
  } finally {
    form.querySelectorAll('input[type="password"]').forEach(input => input.value = '');
    busy = false;
    lock();
  }
});
