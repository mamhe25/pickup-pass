import {
  EmailAuthProvider,
  TotpMultiFactorGenerator,
  getMultiFactorResolver,
  multiFactor,
  reauthenticateWithCredential,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";

export const MFA_REQUIRED_ROLES = new Set([
  "school_admin",
  "master_admin",
]);

export const TOTP_DISPLAY_NAME = "PickupPass Authenticator";
export const TOTP_ISSUER = "PickupPass";

let pendingTotpSecret = null;

export function roleRequiresMfa(role) {
  return MFA_REQUIRED_ROLES.has(role);
}

export function enrolledTotpFactor(user) {
  return multiFactor(user).enrolledFactors.find(
    factor => factor.factorId === TotpMultiFactorGenerator.FACTOR_ID
  ) || null;
}

export function hasEnrolledTotp(user) {
  return Boolean(enrolledTotpFactor(user));
}

export async function authContext(user, forceRefresh = true) {
  const token = await user.getIdTokenResult(forceRefresh);
  const role = token.claims.role || "";
  const firebase = token.claims.firebase || {};
  const secondFactor = firebase.sign_in_second_factor || null;

  return {
    role,
    schoolId: token.claims.schoolId || null,
    mfaRequired: roleRequiresMfa(role),
    mfaSatisfied: Boolean(secondFactor),
    secondFactor,
    emailVerified: Boolean(user.emailVerified),
    mfaEnabled: hasEnrolledTotp(user),
  };
}

export function resolverFor(auth, error) {
  return getMultiFactorResolver(auth, error);
}

export function totpHint(resolver) {
  return resolver.hints.find(
    hint => hint.factorId === TotpMultiFactorGenerator.FACTOR_ID
  ) || null;
}

export async function resolveTotpSignIn(resolver, code) {
  const hint = totpHint(resolver);
  if (!hint) {
    throw new Error("This account uses an unsupported two-factor method.");
  }

  const normalized = normalizeTotpCode(code);
  const assertion = TotpMultiFactorGenerator.assertionForSignIn(
    hint.uid,
    normalized
  );

  return resolver.resolveSignIn(assertion);
}

export async function beginTotpEnrollment(user, currentPassword) {
  if (!user?.email) {
    throw new Error("Please sign in again.");
  }
  if (!user.emailVerified) {
    throw new Error(
      "Verify your sign-in email before enabling two-factor authentication."
    );
  }
  if (hasEnrolledTotp(user)) {
    throw new Error("Two-factor authentication is already enabled.");
  }
  if (!currentPassword) {
    throw new Error("Enter your current password.");
  }

  const credential = EmailAuthProvider.credential(
    user.email,
    currentPassword
  );
  await reauthenticateWithCredential(user, credential);

  const session = await multiFactor(user).getSession();
  pendingTotpSecret =
    await TotpMultiFactorGenerator.generateSecret(session);

  return {
    secretKey: pendingTotpSecret.secretKey,
    qrCodeUrl: pendingTotpSecret.generateQrCodeUrl(
      user.email,
      TOTP_ISSUER
    ),
  };
}

export async function finishTotpEnrollment(user, code) {
  if (!pendingTotpSecret) {
    throw new Error("Start two-factor setup first.");
  }

  const assertion =
    TotpMultiFactorGenerator.assertionForEnrollment(
      pendingTotpSecret,
      normalizeTotpCode(code)
    );

  await multiFactor(user).enroll(
    assertion,
    TOTP_DISPLAY_NAME
  );

  pendingTotpSecret = null;
  await user.getIdToken(true);
}

export function cancelTotpEnrollment() {
  pendingTotpSecret = null;
}

export async function disableTotp(
  user,
  currentPassword,
  role
) {
  if (roleRequiresMfa(role)) {
    throw new Error(
      "Two-factor authentication is required for this administrator role."
    );
  }
  if (!user?.email) {
    throw new Error("Please sign in again.");
  }
  if (!currentPassword) {
    throw new Error("Enter your current password.");
  }

  const factor = enrolledTotpFactor(user);
  if (!factor) {
    throw new Error("Two-factor authentication is not enabled.");
  }

  const credential = EmailAuthProvider.credential(
    user.email,
    currentPassword
  );
  await reauthenticateWithCredential(user, credential);

  try {
    await multiFactor(user).unenroll(factor.uid);
  } catch (error) {
    // Firebase can expire the token after removing the last factor.
    // Re-check local enrollment before deciding whether this is a failure.
    if (error.code !== "auth/user-token-expired" || hasEnrolledTotp(user)) {
      throw error;
    }
  }

  pendingTotpSecret = null;
}

export function normalizeTotpCode(value) {
  const code = String(value || "").replace(/\D/g, "").slice(0, 6);
  if (!/^\d{6}$/.test(code)) {
    throw new Error(
      "Enter the 6-digit code from your authenticator app."
    );
  }
  return code;
}

export function mfaError(error) {
  const messages = {
    "auth/invalid-verification-code":
      "That authenticator code is invalid or expired.",
    "auth/missing-verification-code":
      "Enter the 6-digit code from your authenticator app.",
    "auth/invalid-credential":
      "Your current password or authenticator code is incorrect.",
    "auth/wrong-password":
      "Your current password is incorrect.",
    "auth/requires-recent-login":
      "For security, sign in again and retry.",
    "auth/too-many-requests":
      "Too many attempts. Wait a few minutes and try again.",
    "auth/network-request-failed":
      "Check your connection and try again.",
    "auth/user-token-expired":
      "Your sign-in has expired. Sign in again.",
  };

  return messages[error?.code]
    || error?.message
    || "Two-factor authentication could not be updated. Please try again.";
}
