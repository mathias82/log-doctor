const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const appSource = fs.readFileSync(
  path.join(__dirname, '../../main/resources/web/app.js'),
  'utf8'
);

test('dashboard renders remediation metadata supplied by backend', () => {
  assert.match(appSource, /remediationBlock\(i\.remediation\)/);
  assert.match(appSource, /m\.allowedActions/);
  assert.match(appSource, /m\.verificationSteps/);
  assert.match(appSource, /m\.automaticExecutionAllowed/);
});

test('dashboard does not duplicate category remediation policy', () => {
  assert.doesNotMatch(appSource, /INFRASTRUCTURE:\s*\[/);
  assert.doesNotMatch(appSource, /CONFIGURATION:\s*\[/);
  assert.doesNotMatch(appSource, /function remediationMeta\(/);
});
