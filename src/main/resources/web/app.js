const logInput = document.getElementById('logInput');
const fileInput = document.getElementById('fileInput');
const dropzone = document.getElementById('dropzone');
const analyzeBtn = document.getElementById('analyzeBtn');
const clearBtn = document.getElementById('clearBtn');
const copyBtn = document.getElementById('copyBtn');
const meta = document.getElementById('meta');
const emptyState = document.getElementById('emptyState');
const resultState = document.getElementById('resultState');
const loadingState = document.getElementById('loadingState');
const diagnosisOutput = document.getElementById('diagnosisOutput');
const resultBadge = document.getElementById('resultBadge');
const modeValue = document.getElementById('modeValue');
const safetyValue = document.getElementById('safetyValue');

function updateMeta() {
  const value = logInput.value;
  const lines = value ? value.split(/\r?\n/).length : 0;
  meta.textContent = `${lines.toLocaleString()} lines · ${(new Blob([value]).size / 1024).toFixed(1)} KB`;
}

function setState(state) {
  emptyState.classList.toggle('hidden', state !== 'empty');
  resultState.classList.toggle('hidden', state !== 'result');
  loadingState.classList.toggle('hidden', state !== 'loading');
}

function classifyDiagnosis(text) {
  const lower = text.toLowerCase();
  const usesLlm = lower.includes('llm analysis:');
  const requiresHuman = lower.includes('human investigation required');

  modeValue.textContent = usesLlm ? 'Rules + local LLM' : 'Deterministic rules';
  safetyValue.textContent = requiresHuman ? 'Human review required' : 'Safe remediation available';
  resultBadge.textContent = requiresHuman ? 'Review' : 'Diagnosed';
  resultBadge.className = `badge ${requiresHuman ? 'warn' : 'good'}`;
}

async function analyze() {
  const log = logInput.value.trim();
  if (!log) {
    resultBadge.textContent = 'Add logs';
    resultBadge.className = 'badge warn';
    logInput.focus();
    return;
  }

  analyzeBtn.disabled = true;
  resultBadge.textContent = 'Analyzing';
  resultBadge.className = 'badge neutral';
  setState('loading');

  try {
    const response = await fetch('/api/analyze', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ log })
    });

    const payload = await response.json();
    if (!response.ok) {
      throw new Error(payload.detail || payload.error || 'Analysis failed');
    }

    diagnosisOutput.textContent = payload.diagnosis;
    classifyDiagnosis(payload.diagnosis);
    setState('result');
  } catch (error) {
    diagnosisOutput.textContent = `Analysis failed\n\n${error.message}`;
    modeValue.textContent = 'Unavailable';
    safetyValue.textContent = 'No change applied';
    resultBadge.textContent = 'Error';
    resultBadge.className = 'badge bad';
    setState('result');
  } finally {
    analyzeBtn.disabled = false;
  }
}

async function readFile(file) {
  if (!file) return;
  if (file.size > 5 * 1024 * 1024) {
    resultBadge.textContent = 'File too large';
    resultBadge.className = 'badge bad';
    return;
  }
  logInput.value = await file.text();
  updateMeta();
}

logInput.addEventListener('input', updateMeta);
analyzeBtn.addEventListener('click', analyze);
clearBtn.addEventListener('click', () => {
  logInput.value = '';
  fileInput.value = '';
  diagnosisOutput.textContent = '';
  resultBadge.textContent = 'Ready';
  resultBadge.className = 'badge neutral';
  updateMeta();
  setState('empty');
});
copyBtn.addEventListener('click', async () => {
  await navigator.clipboard.writeText(diagnosisOutput.textContent);
  copyBtn.textContent = 'Copied';
  setTimeout(() => copyBtn.textContent = 'Copy diagnosis', 1200);
});
fileInput.addEventListener('change', event => readFile(event.target.files[0]));

['dragenter', 'dragover'].forEach(name => dropzone.addEventListener(name, event => {
  event.preventDefault();
  dropzone.classList.add('dragging');
}));
['dragleave', 'drop'].forEach(name => dropzone.addEventListener(name, event => {
  event.preventDefault();
  dropzone.classList.remove('dragging');
}));
dropzone.addEventListener('drop', event => readFile(event.dataTransfer.files[0]));

document.addEventListener('keydown', event => {
  if ((event.metaKey || event.ctrlKey) && event.key === 'Enter') analyze();
});

updateMeta();
