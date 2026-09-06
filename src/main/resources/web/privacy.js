(async function loadPrivacyStatus(){
  const byId=id=>document.getElementById(id);
  try {
    const response=await fetch('/api/privacy',{headers:{'Accept':'application/json'}});
    if(!response.ok) throw new Error('Privacy status unavailable');
    const status=await response.json();
    const remote=Boolean(status.logsLeaveMachineForConfiguredLlm);
    byId('privacySummary').textContent=status.summary||'Runtime privacy boundary available.';
    byId('privacyDeterministic').textContent=status.deterministicAnalysis==='LOCAL'?'Local':'Unknown';
    byId('privacyLlmScope').textContent=status.llmEndpointScope==='LOCAL_MACHINE'?'Local machine':'Remote configured';
    byId('privacyRedaction').textContent=status.redactionBeforeLlm?'Before LLM':'Unknown';
    byId('privacyAutomation').textContent=status.automaticRemediation?'Enabled':'Disabled';
    const badge=byId('privacyModeBadge');
    badge.textContent=remote?'Remote LLM configured':'Local-only configured';
    badge.className=`badge ${remote?'warn':'good'}`;
    const header=byId('privacyBadge');
    header.innerHTML='<span class="dot"></span> '+(remote?'Remote LLM configured':'Local-only configured');
  } catch(e) {
    byId('privacySummary').textContent='Could not verify the runtime privacy boundary.';
    const badge=byId('privacyModeBadge');
    badge.textContent='Unknown';
    badge.className='badge warn';
    byId('privacyBadge').innerHTML='<span class="dot"></span> Privacy status unknown';
  }
})();
