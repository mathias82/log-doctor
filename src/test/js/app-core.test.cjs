const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const core = require(path.resolve(__dirname, '../../main/resources/web/app-core.js'));

test('esc prevents incident data from becoming dashboard markup', () => {
  assert.equal(
    core.esc('<script>alert("x")</script> & test'),
    '&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt; &amp; test'
  );
});

test('tone maps severe and review states consistently', () => {
  assert.equal(core.tone('CRITICAL'), 'bad');
  assert.equal(core.tone('HIGH'), 'warn');
  assert.equal(core.tone('NONE'), 'neutral');
  assert.equal(core.tone('LOW'), 'good');
});

test('validLogFile accepts supported log inputs only', () => {
  assert.equal(core.validLogFile({name: 'service.LOG', type: ''}), true);
  assert.equal(core.validLogFile({name: 'notes.txt', type: ''}), true);
  assert.equal(core.validLogFile({name: 'stream.data', type: 'text/plain'}), true);
  assert.equal(core.validLogFile({name: 'archive.zip', type: 'application/zip'}), false);
});

test('history entry keeps only dashboard summary metadata', () => {
  const payload = {
    uniqueIncidents: 2,
    failureBlocks: 5,
    incidents: [{type: 'DATABASE_TIMEOUT', evidence: 'raw secret log'}],
    rootCauseChains: [{score: 80}],
    spikes: [{score: 70}],
    reportMarkdown: 'raw report'
  };

  const entry = core.historyEntry(payload, '2026-09-01T12:00:00.000Z');

  assert.deepEqual(entry, {
    at: '2026-09-01T12:00:00.000Z',
    unique: 2,
    failures: 5,
    top: 'DATABASE_TIMEOUT',
    chains: 1,
    spikes: 1
  });
  assert.equal(Object.hasOwn(entry, 'evidence'), false);
  assert.equal(Object.hasOwn(entry, 'reportMarkdown'), false);
});

test('boundedHistory keeps newest ten analyses', () => {
  const existing = Array.from({length: 10}, (_, i) => ({at: String(i)}));
  const result = core.boundedHistory(existing, {at: 'new'});

  assert.equal(result.length, 10);
  assert.equal(result[0].at, 'new');
  assert.equal(result[9].at, '8');
});
