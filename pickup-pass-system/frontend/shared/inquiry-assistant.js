import { API_BASE_URL } from './firebase-init.js';

const MAX_HISTORY_TURNS = 8;
const MAX_MESSAGE_CHARS = 800;

const launcher = document.getElementById('inquiryLauncher');
const panel = document.getElementById('inquiryPanel');
const closeButton = document.getElementById('inquiryClose');
const messages = document.getElementById('inquiryMessages');
const form = document.getElementById('inquiryForm');
const input = document.getElementById('inquiryInput');
const sendButton = document.getElementById('inquirySend');
const starterButtons = Array.from(document.querySelectorAll('[data-inquiry-starter]'));

if (launcher && panel && closeButton && messages && form && input && sendButton) {
  const history = [];
  let busy = false;

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

  function addMessage(role, text, { status = false } = {}) {
    const bubble = document.createElement('div');
    bubble.className = status
      ? 'pp-inquiry-message pp-inquiry-message--status'
      : `pp-inquiry-message pp-inquiry-message--${role}`;
    bubble.textContent = text;
    messages.appendChild(bubble);
    scrollToLatest();
    return bubble;
  }

  function remember(role, content) {
    history.push({ role, content });
    if (history.length > MAX_HISTORY_TURNS) {
      history.splice(0, history.length - MAX_HISTORY_TURNS);
    }
  }

  function setBusy(nextBusy) {
    busy = nextBusy;
    input.disabled = nextBusy;
    sendButton.disabled = nextBusy;
    starterButtons.forEach(button => {
      button.disabled = nextBusy;
    });
    sendButton.setAttribute('aria-busy', String(nextBusy));
  }

  function friendlyError(status) {
    if (status === 429) {
      return 'You have sent several questions quickly. Please wait a few minutes and try again.';
    }
    if (status === 503) {
      return 'The PickupPass inquiry assistant is temporarily unavailable. You can still request a demo from the main page.';
    }
    return 'I could not answer that right now. Please try again shortly or request a demo for a school-specific question.';
  }

  async function askQuestion(rawMessage) {
    if (busy) return;

    const message = String(rawMessage || '').trim();
    if (!message) return;
    if (message.length > MAX_MESSAGE_CHARS) {
      addMessage('assistant', `Please keep questions under ${MAX_MESSAGE_CHARS} characters.`);
      return;
    }

    openPanel();
    addMessage('user', message);

    const payloadHistory = history.slice();
    remember('user', message);
    input.value = '';
    resizeInput();
    setBusy(true);

    const thinking = addMessage('assistant', 'Checking the PickupPass product information…', { status: true });

    try {
      const response = await fetch(`${API_BASE_URL}/public/inquiry/chat`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          message,
          history: payloadHistory
        })
      });

      if (!response.ok) {
        throw Object.assign(new Error('Inquiry request failed'), { status: response.status });
      }

      const body = await response.json();
      const answer = String(body?.answer || '').trim();
      if (!answer) {
        throw new Error('Empty assistant response');
      }

      thinking.remove();
      addMessage('assistant', answer);
      remember('assistant', answer);
    } catch (error) {
      thinking.remove();
      addMessage('assistant', friendlyError(error?.status));
    } finally {
      setBusy(false);
      input.focus({ preventScroll: true });
    }
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
    'Hi! I’m the PickupPass Inquiry Assistant. Ask me about pickup passes, guardian verification, school setup, launch testing, security, or how the dismissal workflow works.'
  );
}
