import {
  auth,
  authedFetch,
  setSubmitButtonBusy,
} from "./firebase-init.js";

import * as sdk
  from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";

import {
  createAccountActions,
  accountError,
} from "./account-actions.js";

import {
  authContext,
  beginTotpEnrollment,
  cancelTotpEnrollment,
  disableTotp,
  finishTotpEnrollment,
  mfaError,
} from "./mfa.js";

const actions = createAccountActions(sdk);

const forms = [...document.querySelectorAll("form")];
const homes = {
  parent: "parent/students.html",
  teacher: "teacher/scanner.html",
  school_admin: "school-admin/dashboard.html",
  master_admin: "master-admin/overview.html",
};

const el = id => document.getElementById(id);
const show = (element, visible) =>
  element?.classList.toggle("hidden", !visible);

let ready = false;
let busy = false;
let currentContext = null;
let setupUri = null;

const lock = () => {
  forms.forEach(form => {
    form.querySelector("fieldset").disabled =
      !ready || busy;
  });
};

function setStatus(
  element,
  message = "",
  type = "info"
) {
  if (!element) return;

  element.textContent = message;
  element.dataset.error =
    type === "error" ? "true" : "false";
  element.dataset.success =
    type === "success" ? "true" : "false";
}

function setMfaBusy(value) {
  busy = value;
  lock();

  [
    "sendMfaVerification",
    "refreshMfaVerification",
    "beginMfaButton",
    "finishMfaButton",
    "cancelMfaSetup",
    "disableMfaButton",
  ].forEach(id => {
    const button = el(id);
    if (button) button.disabled = value;
  });
}

function renderMfa(context) {
  currentContext = context;

  const policy = el("mfaPolicyBadge");
  const state = el("mfaStateBadge");
  const emailBadge = el("emailVerifiedBadge");

  policy.textContent =
    context.mfaRequired ? "REQUIRED" : "OPTIONAL";
  policy.dataset.state =
    context.mfaRequired ? "required" : "optional";

  state.textContent =
    context.mfaEnabled ? "ON" : "OFF";
  state.dataset.state =
    context.mfaEnabled ? "on" : "off";

  emailBadge.textContent =
    context.emailVerified
      ? "Email verified"
      : "Email unverified";
  emailBadge.dataset.state =
    context.emailVerified
      ? "verified"
      : "unverified";

  el("mfaDescription").textContent =
    context.mfaRequired
      ? "Your administrator role requires an authenticator code after your password."
      : "Add a rotating 6-digit authenticator code after your password.";

  show(
    el("requiredMfaNotice"),
    context.mfaRequired
  );

  show(
    el("mfaEnabledSection"),
    context.mfaEnabled
  );

  show(
    el("optionalDisablePanel"),
    context.mfaEnabled && !context.mfaRequired
  );

  if (context.mfaEnabled) {
    show(el("mfaUnverified"), false);
    show(el("mfaSetupStart"), false);
    show(el("mfaSetupConfirm"), false);
    return;
  }

  show(
    el("mfaUnverified"),
    !context.emailVerified
  );
  show(
    el("mfaSetupStart"),
    context.emailVerified && !setupUri
  );
  show(
    el("mfaSetupConfirm"),
    context.emailVerified && Boolean(setupUri)
  );
}

async function refreshAccount() {
  const user = auth.currentUser;
  if (!user) {
    location.replace("./login.html");
    return;
  }

  await sdk.reload(user);
  const context = await authContext(user, true);

  const home = homes[context.role];
  if (!home) {
    throw new Error(
      "This account does not have access to PickupPass."
    );
  }

  /*
   * An enrolled protected role must never keep using a first-factor-only
   * cached session. Force a fresh Firebase sign-in so the authenticator
   * challenge occurs.
   */
  if (
    context.mfaRequired &&
    context.mfaEnabled &&
    !context.mfaSatisfied
  ) {
    await sdk.signOut(auth);
    location.replace(
      "./login.html?mfa=required"
    );
    return;
  }

  const backLink = el("backLink");
  if (context.mfaRequired && !context.mfaEnabled) {
    backLink.href = "./login.html";
    backLink.textContent = "← Sign out instead";
    backLink.dataset.requiredMfaSetup = "true";
  } else {
    backLink.href = "./" + home;
    backLink.textContent = "← Back to PickupPass";
    delete backLink.dataset.requiredMfaSetup;
  }

  el("currentEmail").textContent =
    user.email || "";

  renderMfa(context);

  ready = true;
  lock();

  // This endpoint is intentionally permitted for mandatory-MFA onboarding.
  const response = await authedFetch("/session/me");
  if (!response.ok) {
    throw new Error(
      "Could not refresh your profile. Please sign in again."
    );
  }
}

sdk.onAuthStateChanged(
  auth,
  async user => {
    ready = false;
    lock();

    if (!user) {
      location.replace("./login.html");
      return;
    }

    try {
      await refreshAccount();
    } catch (error) {
      setStatus(
        el("accountStatus"),
        accountError(error),
        "error"
      );
    }
  }
);

for (const form of forms) {
  form.addEventListener(
    "submit",
    async event => {
      event.preventDefault();

      if (
        !ready ||
        busy ||
        !form.reportValidity()
      ) {
        return;
      }

      busy = true;
      lock();

      const status =
        form.querySelector(".form-status");

      setStatus(
        status,
        "Please wait…"
      );

      try {
        if (form.id === "emailForm") {
          const email =
            await actions.changeEmail(
              auth.currentUser,
              el("emailPassword").value,
              el("newEmail").value
            );

          setStatus(
            status,
            `Verification email sent to ${email}. ` +
              "Check your inbox, verify the link, then sign in with the new email.",
            "success"
          );
        } else {
          await actions.changePassword(
            auth.currentUser,
            el("currentPassword").value,
            el("newPassword").value,
            el("confirmPassword").value
          );

          setStatus(
            status,
            "Your password has been changed.",
            "success"
          );
        }

        form.reset();
      } catch (error) {
        setStatus(
          status,
          accountError(error),
          "error"
        );
      } finally {
        form
          .querySelectorAll('input[type="password"]')
          .forEach(input => {
            input.value = "";
          });

        busy = false;
        lock();
      }
    }
  );
}

el("sendMfaVerification")
  .addEventListener(
    "click",
    async () => {
      if (busy) return;

      setMfaBusy(true);
      setStatus(el("mfaStatus"));

      try {
        await sdk.sendEmailVerification(
          auth.currentUser
        );
        setStatus(
          el("mfaStatus"),
          "Verification email sent. Open the link, then select “I’ve verified”.",
          "success"
        );
      } catch (error) {
        setStatus(
          el("mfaStatus"),
          mfaError(error),
          "error"
        );
      } finally {
        setMfaBusy(false);
      }
    }
  );

el("refreshMfaVerification")
  .addEventListener(
    "click",
    async () => {
      if (busy) return;

      setMfaBusy(true);
      setStatus(el("mfaStatus"));

      try {
        await refreshAccount();

        if (
          !auth.currentUser?.emailVerified
        ) {
          setStatus(
            el("mfaStatus"),
            "Your email is not verified yet. Open the verification link, then try again.",
            "error"
          );
        } else {
          setStatus(
            el("mfaStatus"),
            "Email verified. You can now set up your authenticator.",
            "success"
          );
        }
      } catch (error) {
        setStatus(
          el("mfaStatus"),
          mfaError(error),
          "error"
        );
      } finally {
        setMfaBusy(false);
      }
    }
  );

el("beginMfaButton")
  .addEventListener(
    "click",
    async () => {
      if (busy) return;

      const password =
        el("mfaPassword").value;

      setMfaBusy(true);
      setStatus(el("mfaStatus"));

      try {
        const setup =
          await beginTotpEnrollment(
            auth.currentUser,
            password
          );

        setupUri = setup.qrCodeUrl;

        el("totpSetupKey").textContent =
          setup.secretKey;

        el("authenticatorLink").href =
          setup.qrCodeUrl;

        el("mfaPassword").value = "";

        renderMfa(currentContext);
      } catch (error) {
        setStatus(
          el("mfaStatus"),
          mfaError(error),
          "error"
        );
      } finally {
        setMfaBusy(false);
      }
    }
  );

el("mfaCode").addEventListener(
  "input",
  event => {
    event.target.value =
      event.target.value
        .replace(/\D/g, "")
        .slice(0, 6);
  }
);

el("finishMfaButton")
  .addEventListener(
    "click",
    async () => {
      if (busy) return;

      setMfaBusy(true);
      setStatus(el("mfaStatus"));

      try {
        await finishTotpEnrollment(
          auth.currentUser,
          el("mfaCode").value
        );

        setupUri = null;
        el("mfaCode").value = "";

        const context =
          await authContext(
            auth.currentUser,
            true
          );

        renderMfa(context);

        if (context.mfaRequired) {
          /*
           * Required roles perform a fresh sign-in so their next Firebase ID
           * token proves the second factor was actually presented.
           */
          await sdk.signOut(auth);
          location.replace(
            "./login.html?mfa=enabled"
          );
          return;
        }

        setStatus(
          el("mfaStatus"),
          "Two-factor authentication is enabled. Your next sign-in will require an authenticator code.",
          "success"
        );
      } catch (error) {
        setStatus(
          el("mfaStatus"),
          mfaError(error),
          "error"
        );
      } finally {
        setMfaBusy(false);
      }
    }
  );

el("cancelMfaSetup")
  .addEventListener(
    "click",
    () => {
      cancelTotpEnrollment();
      setupUri = null;
      el("mfaCode").value = "";
      el("totpSetupKey").textContent = "";
      renderMfa(currentContext);
      setStatus(el("mfaStatus"));
    }
  );

el("disableMfaButton")
  .addEventListener(
    "click",
    async () => {
      if (busy) return;

      const password =
        el("disableMfaPassword").value;

      setMfaBusy(true);
      setStatus(el("mfaStatus"));

      try {
        await disableTotp(
          auth.currentUser,
          password,
          currentContext?.role
        );

        el("disableMfaPassword").value = "";

        if (!auth.currentUser) {
          location.replace(
            "./login.html?mfa=disabled"
          );
          return;
        }

        await refreshAccount();

        setStatus(
          el("mfaStatus"),
          "Two-factor authentication is disabled for this account.",
          "success"
        );
      } catch (error) {
        if (
          error?.code ===
            "auth/user-token-expired"
        ) {
          location.replace(
            "./login.html?mfa=disabled"
          );
          return;
        }

        setStatus(
          el("mfaStatus"),
          mfaError(error),
          "error"
        );
      } finally {
        setMfaBusy(false);
      }
    }
  );


el("backLink").addEventListener(
  "click",
  async event => {
    if (el("backLink").dataset.requiredMfaSetup !== "true") {
      return;
    }

    event.preventDefault();
    cancelTotpEnrollment();
    await sdk.signOut(auth);
    location.replace("./login.html");
  }
);
