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

test('dashboard renders backend remediation playbook phases', () => {
  assert.match(appSource, /playbookBlock\(m\.playbook\)/);
  assert.match(appSource, /p\.inspect/);
  assert.match(appSource, /p\.changeCandidates/);
  assert.match(appSource, /p\.validate/);
  assert.match(appSource, /p\.escalationSignals/);
  assert.match(appSource, /Investigation guidance only · never executed automatically/);
});

test('dashboard consumes structured grouping metadata instead of fingerprint delimiters', () => {
  assert.match(appSource, /groupingBlock\(i\.grouping\)/);
  assert.match(appSource, /grouping\.exceptionType/);
  assert.match(appSource, /grouping\.frames/);
  assert.match(appSource, /grouping\.lineNumbersIgnored/);
  assert.doesNotMatch(appSource, /fingerprint\.split\('\|'\)/);
  assert.doesNotMatch(appSource, /split\('>'\)/);
});

test('dashboard does not duplicate category remediation policy', () => {
  assert.doesNotMatch(appSource, /INFRASTRUCTURE:\s*\[/);
  assert.doesNotMatch(appSource, /CONFIGURATION:\s*\[/);
  assert.doesNotMatch(appSource, /function remediationMeta\(/);
});
