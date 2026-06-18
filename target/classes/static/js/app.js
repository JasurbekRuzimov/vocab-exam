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