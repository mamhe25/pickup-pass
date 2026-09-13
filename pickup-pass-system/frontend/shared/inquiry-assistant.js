const MAX_MESSAGE_CHARS = 800;

const launcher = document.getElementById('inquiryLauncher');
const panel = document.getElementById('inquiryPanel');
const closeButton = document.getElementById('inquiryClose');
const messages = document.getElementById('inquiryMessages');
const form = document.getElementById('inquiryForm');
const input = document.getElementById('inquiryInput');
const sendButton = document.getElementById('inquirySend');
const starterButtons = Array.from(document.querySelectorAll('[data-inquiry-starter]'));

const STOP_WORDS = new Set([
  'a', 'an', 'and', 'are', 'as', 'at', 'be', 'can', 'do', 'does', 'for', 'from',
  'how', 'i', 'in', 'is', 'it', 'me', 'my', 'of', 'on', 'or', 'the', 'to',
  'what', 'when', 'where', 'which', 'who', 'why', 'with', 'you', 'your'
]);

const SENSITIVE_PATTERN = /\b(password|passcode|otp|one[- ]?time code|authenticator code|verification code|qr token|secret key|api key|government id|student record|guardian record)\b/i;

const KNOWLEDGE = [
  {
    id: 'overview',
    phrases: ['what is pickuppass', 'what does pickuppass do', 'about pickuppass'],
    keywords: ['pickuppass', 'overview', 'platform', 'system', 'dismissal', 'pickup'],
    answer: 'PickupPass is a multi-school digital dismissal and pickup-pass system. It connects Parents/Guardians, Teachers/Staff, School Admins, and the Platform Owner in one controlled pickup workflow: authorized guardians present a short-lived pass, staff verify identity, the release is explicitly approved, and the completed handoff is recorded.'
  },
  {
    id: 'guardian-verification',
    phrases: ['guardian verification', 'verify guardian', 'guardian photo', 'photo verification', 'face verification'],
    keywords: ['guardian', 'verification', 'verify', 'photo', 'face', 'identity', 'authorized'],
    answer: 'Guardian verification has two layers. A guardian verification photo must first pass server-side face detection before the guardian becomes pickup-pass ready. At the gate, staff still visually compare the person present with the authorized guardian identity before approving the student release. A valid QR alone is not enough.'
  },
  {
    id: 'pickup-pass',
    phrases: ['pickup pass', 'qr pass', 'generate qr', 'qr code', 'pickup qr'],
    keywords: ['pass', 'qr', 'code', 'generate', 'expire', 'token', 'pickup'],
    answer: 'Eligible parents or guardians can generate a short-lived QR pickup pass for a linked student. The pass is designed to expire rather than act like a permanent reusable card. When scanned, PickupPass checks the pickup context and then shows staff the guardian-verification step before any release can be approved.'
  },
  {
    id: 'scanner',
    phrases: ['scan pass', 'scanner work', 'when scanned', 'teacher scanner', 'dismissal scanner'],
    keywords: ['scan', 'scanner', 'camera', 'gate', 'verify', 'release'],
    answer: 'The staff scanner reads the pickup pass and validates the current pickup context. Staff then review the authorized guardian information and explicitly approve the release. The scanner is a verification tool, not an automatic release button.'
  },
  {
    id: 'prelaunch',
    phrases: ['pre launch', 'pre-launch', 'test mode', 'launch testing', 'before launch'],
    keywords: ['launch', 'testing', 'test', 'approve', 'approval', 'readiness', 'prelaunch'],
    answer: 'New schools can operate in pre-launch test mode so administrators and staff can verify setup, devices, scanner behavior, and the dismissal workflow. Real dismissal release remains restricted until the school completes launch readiness and the Platform Owner approves launch.'
  },
  {
    id: 'notifications',
    phrases: ['notifications work', 'push notification', 'notification badge', 'real time notification'],
    keywords: ['notification', 'notifications', 'push', 'badge', 'alert', 'in-app', 'realtime'],
    answer: 'PickupPass supports in-app and push notification flows. Supported events can update unread badges without requiring a manual refresh, and tapping a notification can route the user to the related PickupPass screen.'
  },
  {
    id: 'parents',
    phrases: ['parent role', 'what can parents do', 'parent account'],
    keywords: ['parent', 'parents', 'family', 'families', 'guardian', 'student', 'pass'],
    answer: 'Parents can work with their linked students, manage authorized guardians, maintain their verification photo, generate eligible pickup passes, receive notifications, review profile/security settings, and manage signed-in device sessions.'
  },
  {
    id: 'teachers',
    phrases: ['teacher role', 'what can teachers do', 'teacher account', 'staff role'],
    keywords: ['teacher', 'teachers', 'staff', 'section', 'students', 'scanner'],
    answer: 'Teachers and staff work with students in the sections assigned to them by the School Admin. Their workflow includes the dismissal scanner, student and guardian-related tasks available to staff, notifications, announcements, operations, and dismissal history.'
  },
  {
    id: 'school-admin',
    phrases: ['school admin', 'school administrator', 'admin role', 'what can admin do'],
    keywords: ['admin', 'administrator', 'school', 'academic', 'section', 'staff', 'settings'],
    answer: 'School Admins manage the school-side configuration: academic years, grades and sections, teacher assignments, students, pickup settings, campuses and gates, announcements, launch readiness, branding, billing views, reporting, and audit/history tools.'
  },
  {
    id: 'platform-owner',
    phrases: ['platform owner', 'master admin', 'owner role'],
    keywords: ['owner', 'platform', 'master', 'schools', 'subscription', 'operations', 'recovery'],
    answer: 'The Platform Owner oversees the multi-school platform: school provisioning, administrators, plans/subscriptions, launch approval, platform operations, security oversight, billing administration, and recovery controls.'
  },
  {
    id: 'security',
    phrases: ['is pickuppass secure', 'security', 'role based access', 'access control'],
    keywords: ['security', 'secure', 'access', 'role', 'roles', 'audit', 'protected', 'identity'],
    answer: 'PickupPass uses role-scoped access, Firebase Authentication, server-authorized actions, guardian identity checks, auditable release history, and device/session controls. Privileged Platform Owner and School Admin accounts also require two-factor authentication.'
  },
  {
    id: 'mfa',
    phrases: ['two factor', '2fa', 'mfa', 'authenticator app'],
    keywords: ['mfa', '2fa', 'authenticator', 'factor', 'security', 'code'],
    answer: 'Two-factor authentication is required for Platform Owner and School Admin accounts. Parent and Teacher accounts can use it optionally. PickupPass uses a six-digit authenticator-code flow for supported TOTP verification.'
  },
  {
    id: 'sessions',
    phrases: ['signed in devices', 'logout other devices', 'revoke device', 'device sessions'],
    keywords: ['device', 'devices', 'session', 'sessions', 'logout', 'signout', 'revoke'],
    answer: 'PickupPass includes signed-in device/session controls. Users can review their sessions and revoke other devices. A revoked device is blocked by the backend rather than relying only on the screen to sign it out.'
  },
  {
    id: 'history',
    phrases: ['dismissal history', 'release history', 'pickup history', 'audit release'],
    keywords: ['history', 'release', 'dismissal', 'record', 'records', 'audit', 'timestamp'],
    answer: 'Approved student releases are recorded for later review. Dismissal history preserves the important handoff context, such as the student, guardian involved, approving staff, and recorded time.'
  },
  {
    id: 'academic',
    phrases: ['academic structure', 'grade section', 'school year', 'teacher assignment'],
    keywords: ['academic', 'year', 'grade', 'section', 'assignment', 'teacher', 'roster'],
    answer: 'School Admins define the current academic year and the school’s grade/section structure. Teacher assignments and student placement use that configured structure, so teachers do not create arbitrary grade or section names during normal registration.'
  },
  {
    id: 'guardians',
    phrases: ['multiple guardians', 'backup guardian', 'temporary guardian', 'authorized guardian'],
    keywords: ['guardian', 'guardians', 'backup', 'temporary', 'primary', 'authorized', 'schedule'],
    answer: 'PickupPass supports primary and additional authorized guardian relationships around a student, including backup or temporary access where configured. Guardian authorization remains tied to the student and school pickup rules.'
  },
  {
    id: 'accounts',
    phrases: ['need account', 'create account', 'cannot sign in', 'cant sign in', 'forgot password', 'login problem'],
    keywords: ['account', 'login', 'signin', 'password', 'access', 'reset', 'credentials'],
    answer: 'PickupPass accounts are normally issued through the school. If you need an account, contact your School Admin. If you already have an account and forgot your password, enter your email on this sign-in page and use “Forgot password?” to request a reset email.'
  },
  {
    id: 'pricing',
    phrases: ['how much', 'price', 'pricing', 'cost', 'subscription fee', 'is it free'],
    keywords: ['price', 'pricing', 'cost', 'fee', 'fees', 'subscription', 'plan', 'free'],
    answer: 'This assistant does not publish or guess commercial pricing. Plans, rollout terms, and school-specific requirements should be confirmed through a PickupPass demo or direct discussion with the platform team.'
  },
  {
    id: 'onboarding',
    phrases: ['school setup', 'onboarding', 'implementation', 'rollout', 'get started'],
    keywords: ['setup', 'onboarding', 'implementation', 'rollout', 'school', 'start', 'demo'],
    answer: 'A typical PickupPass rollout covers school setup, administrators and staff, academic structure, student/guardian data, pickup configuration, scanner/device testing, and the launch-readiness checklist. The school can test the full workflow before Platform Owner approval enables real dismissal release.'
  },
  {
    id: 'billing',
    phrases: ['billing', 'invoice', 'payment', 'subscription'],
    keywords: ['billing', 'invoice', 'payment', 'subscription', 'plan'],
    answer: 'PickupPass includes school and platform billing administration, including subscription/plan views and invoice/payment workflows. For actual prices, contract terms, or a specific school account, request a demo or contact the platform team.'
  }
];

function normalizeText(value) {
  return String(value || '')
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9\s-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function tokensFor(value) {
  return normalizeText(value)
    .split(/[\s-]+/)
    .filter(token => token.length > 1 && !STOP_WORDS.has(token));
}

function scoreEntry(entry, normalized, tokens) {
  let score = 0;

  for (const phrase of entry.phrases || []) {
    if (normalized.includes(normalizeText(phrase))) {
      score += 8;
    }
  }

  const tokenSet = new Set(tokens);
  for (const keyword of entry.keywords || []) {
    const normalizedKeyword = normalizeText(keyword);
    if (normalized.includes(normalizedKeyword)) {
      score += normalizedKeyword.includes(' ') ? 4 : 2;
    }
    if (tokenSet.has(normalizedKeyword)) {
      score += 1;
    }
  }

  return score;
}

function answerQuestion(message) {
  const normalized = normalizeText(message);
  const tokens = tokensFor(message);

  if (!normalized) {
    return 'Ask me a general question about PickupPass and I’ll try to match it with the built-in product guide.';
  }

  if (/^(hi|hello|hey|good morning|good afternoon|good evening)\b/.test(normalized)) {
    return 'Hi! I can help with common PickupPass questions about pickup passes, guardian verification, school setup, launch testing, roles, notifications, security, and the dismissal workflow.';
  }

  if (SENSITIVE_PATTERN.test(message)) {
    return 'For your privacy, please don’t share passwords, authenticator codes, QR tokens, student records, guardian records, IDs, or other private information here. I can still explain the general PickupPass process without those details.';
  }

  const ranked = KNOWLEDGE
    .map(entry => ({ entry, score: scoreEntry(entry, normalized, tokens) }))
    .sort((a, b) => b.score - a.score);

  const best = ranked[0];
  if (!best || best.score < 3) {
    return 'I’m a free built-in PickupPass product guide, so I answer from a fixed set of verified product topics rather than generating answers from the internet. I can help with pickup passes, guardian verification, scanner flow, launch testing, roles, notifications, security, academic setup, device sessions, billing workflow, and onboarding. For a school-specific or commercial question, please request a demo.';
  }

  return best.entry.answer;
}

if (launcher && panel && closeButton && messages && form && input && sendButton) {
  const subtitle = panel.querySelector('.pp-inquiry-panel__title span');
  if (subtitle) {
    subtitle.textContent = 'Free built-in product answers • no paid AI/API calls';
  }

  function openPanel() {
    panel.hidden = false;
    launcher.setAttribute('aria-expanded', 'true');
    window.setTimeout(() => input.focus({ preventScroll: true }), 0);
  }

  function closePanel() {
    panel.hidden = true;
    launcher.setAttribute('aria-expanded', 'false');
    launcher.focus({ preventScroll: true });
  }

  function scrollToLatest() {
    messages.scrollTop = messages.scrollHeight;
  }

  function addMessage(role, text) {
    const bubble = document.createElement('div');
    bubble.className = `pp-inquiry-message pp-inquiry-message--${role}`;
    bubble.textContent = text;
    messages.appendChild(bubble);
    scrollToLatest();
  }

  function askQuestion(rawMessage) {
    const message = String(rawMessage || '').trim();
    if (!message) return;

    if (message.length > MAX_MESSAGE_CHARS) {
      addMessage('assistant', `Please keep questions under ${MAX_MESSAGE_CHARS} characters.`);
      return;
    }

    openPanel();
    addMessage('user', message);
    input.value = '';
    resizeInput();

    const answer = answerQuestion(message);
    window.setTimeout(() => {
      addMessage('assistant', answer);
      input.focus({ preventScroll: true });
    }, 120);
  }

  function resizeInput() {
    input.style.height = 'auto';
    input.style.height = `${Math.min(input.scrollHeight, 104)}px`;
  }

  launcher.addEventListener('click', () => {
    if (panel.hidden) openPanel();
    else closePanel();
  });

  closeButton.addEventListener('click', closePanel);

  starterButtons.forEach(button => {
    button.addEventListener('click', () => {
      askQuestion(button.dataset.inquiryStarter || button.textContent);
    });
  });

  form.addEventListener('submit', event => {
    event.preventDefault();
    askQuestion(input.value);
  });

  input.addEventListener('input', resizeInput);
  input.addEventListener('keydown', event => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      form.requestSubmit();
    }
  });

  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !panel.hidden) {
      closePanel();
    }
  });

  addMessage(
    'assistant',
    'Hi! I’m the free PickupPass Inquiry Assistant. I use a built-in product guide, so there are no paid AI/API calls. Ask me about pickup passes, guardian verification, school setup, launch testing, security, or the dismissal workflow.'
  );
}
