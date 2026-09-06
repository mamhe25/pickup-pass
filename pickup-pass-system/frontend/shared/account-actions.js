// Pure orchestration: Firebase credentials never pass through our API or storage.
export function createAccountActions(sdk) {
  async function reauthenticate(user, password) {
    if (!user?.email) throw new Error('Please sign in again.');
    if (!password) throw new Error('Enter your current password.');
    await sdk.reauthenticateWithCredential(user, sdk.EmailAuthProvider.credential(user.email, password));
  }
  return {
    async changeEmail(user, currentPassword, value) {
      const email = value.trim();
      if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new Error('Enter a valid email address.');
      if (email.toLowerCase() === user?.email?.toLowerCase()) throw new Error('Enter a different email address.');
      await reauthenticate(user, currentPassword);
      await sdk.verifyBeforeUpdateEmail(user, email);
      return email;
    },
    async changePassword(user, currentPassword, password, confirmation) {
      if (password.length < 8) throw new Error('Use at least 8 characters for your new password.');
      if (password !== confirmation) throw new Error('The new passwords do not match.');
      if (password === currentPassword) throw new Error('Choose a password different from your current password.');
      await reauthenticate(user, currentPassword);
      await sdk.updatePassword(user, password);
    }
  };
}

export function accountError(error) {
  const messages = {
    'auth/wrong-password': 'The current password is incorrect.',
    'auth/invalid-credential': 'The current password is incorrect. Please try again.',
    'auth/invalid-login-credentials': 'The current password is incorrect. Please try again.',
    'auth/email-already-in-use': 'That email is already in use. Choose another email.',
    'auth/invalid-email': 'Enter a valid email address.',
    'auth/weak-password': 'Choose a stronger password that meets your account’s password requirements.',
    'auth/password-does-not-meet-requirements': 'Choose a stronger password that meets your account’s password requirements.',
    'auth/requires-recent-login': 'Please sign in again and retry.',
    'auth/user-token-expired': 'Your sign-in has expired. Please sign in again.',
    'auth/user-disabled': 'This account is disabled. Contact your administrator.',
    'auth/too-many-requests': 'Too many attempts. Please wait before trying again.',
    'auth/network-request-failed': 'Check your connection and try again.',
    'auth/operation-not-allowed': 'This account change is unavailable. Contact your administrator.'
  };
  return messages[error.code] || (error.code ? 'Could not update your account. Please try again.' : error.message);
}
