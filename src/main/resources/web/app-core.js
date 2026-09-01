(function(root,factory){
  const api=factory();
  if(typeof module==='object'&&module.exports){module.exports=api;}
  else{root.LogDoctorWebCore=api;}
})(typeof globalThis!=='undefined'?globalThis:this,function(){
  function tone(value){
    const v=(value||'').toUpperCase();
    return v==='CRITICAL'||v==='ERROR'?'bad':v==='HIGH'||v==='MEDIUM'?'warn':v==='NONE'?'neutral':'good';
  }

  function esc(value){
    return String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  }

  function validLogFile(file){
    if(!file)return false;
    const name=(file.name||'').toLowerCase();
    return name.endsWith('.log')||name.endsWith('.txt')||file.type==='text/plain';
  }

  function historyEntry(payload,at){
    return {
      at:at||new Date().toISOString(),
      unique:payload?.uniqueIncidents||0,
      failures:payload?.failureBlocks||0,
      top:payload?.incidents?.[0]?.type||'NO_FAILURE',
      chains:payload?.rootCauseChains?.length||0,
      spikes:payload?.spikes?.length||0
    };
  }

  function boundedHistory(items,entry,limit=10){
    return [entry,...items].slice(0,limit);
  }

  return {tone,esc,validLogFile,historyEntry,boundedHistory};
});
