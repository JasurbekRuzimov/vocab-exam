// CSRF token helper
function getCsrfToken() {
  const meta = document.querySelector('meta[name="_csrf"]');
  return meta ? meta.getAttribute('content') : null;
}

function getCsrfHeader() {
  const meta = document.querySelector('meta[name="_csrf_header"]');
  return meta ? meta.getAttribute('content') : 'X-XSRF-TOKEN';
}

// CSRF bilan fetch wrapper
async function apiFetch(url, options = {}) {
  const token = getCsrfToken();
  const headers = {
    'Content-Type': 'application/json',
    ...(token ? { [getCsrfHeader()]: token } : {}),
    ...options.headers
  };
  return fetch(url, { ...options, headers });
}

// Auto-dismiss alerts
document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('.alert-dismissible').forEach(alert => {
    setTimeout(() => {
      try {
        const a = bootstrap.Alert.getInstance(alert) || new bootstrap.Alert(alert);
        a.close();
      } catch(e) {}
    }, 5000);
  });
});

// Error highlight
function highlightErrors(container, text, errors) {
  if (!text || !errors?.length) { container.textContent = text || ''; return; }
  let result = '', last = 0;
  [...errors].sort((a,b) => a.errorStart - b.errorStart).forEach(err => {
    result += escapeHtml(text.slice(last, err.errorStart));
    result += `<span class="error-highlight" title="${escapeHtml(err.comment)}">${escapeHtml(text.slice(err.errorStart, err.errorEnd))}</span>`;
    last = err.errorEnd;
  });
  result += escapeHtml(text.slice(last));
  container.innerHTML = result;
}

function escapeHtml(str) {
  return (str || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

// Tugmalar uchun umumiy amal (o'chirish, bloklash, nomini o'zgartirish va h.k.):
//   data-api-url, data-api-method (standart POST), data-confirm — tasdiqlash savoli,
//   data-prompt + data-prompt-field (+ data-prompt-value) — qiymat so'rash,
//   data-body — qo'shimcha JSON, data-redirect — muvaffaqiyatdan keyin o'tiladigan sahifa.
document.addEventListener('click', async e => {
  const btn = e.target.closest('[data-api-url]');
  if (!btn) return;
  e.preventDefault();
  const d = btn.dataset;
  let body = d.body ? JSON.parse(d.body) : null;
  if (d.prompt) {
    const value = prompt(d.prompt, d.promptValue || '');
    if (value === null || !value.trim()) return;
    body = { ...(body || {}), [d.promptField]: value.trim() };
  }
  if (d.confirm && !confirm(d.confirm)) return;
  btn.disabled = true;
  try {
    const res = await fetch(d.apiUrl, {
      method: d.apiMethod || 'POST',
      headers: body ? { 'Content-Type': 'application/json' } : {},
      body: body ? JSON.stringify(body) : undefined
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) { alert(data.message || 'Xato yuz berdi'); return; }
    if (d.success) alert(d.success);
    if (d.redirect) location.href = d.redirect; else location.reload();
  } finally {
    btn.disabled = false;
  }
});
