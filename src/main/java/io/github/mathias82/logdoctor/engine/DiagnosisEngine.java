package io.github.mathias82.logdoctor.engine;

import io.github.mathias82.logdoctor.core.Confidence;
import io.github.mathias82.logdoctor.core.FixPolicy;
import io.github.mathias82.logdoctor.core.FixType;
import io.github.mathias82.logdoctor.core.Incident;
import io.github.mathias82.logdoctor.core.IncidentCategory;
import io.github.mathias82.logdoctor.core.RemediationMetadata;
import io.github.mathias82.logdoctor.llm.LlmClient;
import io.github.mathias82.logdoctor.llm.OllamaLlmClient;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class DiagnosisEngine {
    private static final int CONTEXT_RADIUS = 8;
    private static final String NO_AUTOMATIC_FIX = "No safe automatic fix, human investigation required.";
    private final IncidentDetector detector; private final LogParser parser; private final FailureLocator failureLocator;
    private final FailureContextExtractor contextExtractor; private final CauseChainAnalyzer causeChainAnalyzer;
    private final MatchConfidenceScorer matchConfidenceScorer; private final LlmClient llm;

    public DiagnosisEngine(){this(new OllamaLlmClient());}
    public DiagnosisEngine(LlmClient llm){this(new IncidentDetector(),new LogParser(),new FailureLocator(),new FailureContextExtractor(),new CauseChainAnalyzer(),new MatchConfidenceScorer(),llm);}
    DiagnosisEngine(IncidentDetector d,LogParser p,FailureLocator f,FailureContextExtractor c,LlmClient l){this(d,p,f,c,new CauseChainAnalyzer(),new MatchConfidenceScorer(),l);}
    DiagnosisEngine(IncidentDetector d,LogParser p,FailureLocator f,FailureContextExtractor c,CauseChainAnalyzer a,LlmClient l){this(d,p,f,c,a,new MatchConfidenceScorer(),l);}
    DiagnosisEngine(IncidentDetector d,LogParser p,FailureLocator f,FailureContextExtractor c,CauseChainAnalyzer a,MatchConfidenceScorer m,LlmClient l){detector=d;parser=p;failureLocator=f;contextExtractor=c;causeChainAnalyzer=a;matchConfidenceScorer=m;llm=l;}
    public void analyze(String log){System.out.print(analyzeToText(log));}
    public String analyzeToText(String log){return analyzeStructured(log).diagnosis();}
    public DiagnosisResult analyzeStructured(String log){return analyzeStructured(log,true);}

    DiagnosisResult analyzeStructured(String log,boolean allowLlm){
        if(log==null||log.isBlank())return DiagnosisResult.empty("No log content provided.");
        var lines=parser.parse(log); var failureOpt=failureLocator.locate(lines); if(failureOpt.isEmpty())return DiagnosisResult.empty("No obvious failure found.");
        var failure=failureOpt.get(); String contextText=contextExtractor.extract(lines,failure,CONTEXT_RADIUS);
        String location=failure.blameLocation()!=null?failure.blameLocation().content():failure.rootCause().content();
        List<CauseChainAnalyzer.Cause> chain=causeChainAnalyzer.analyze(lines);
        var detectionOpt=detector.detectDetailed(new RuleContext(lines,failure,contextText)).filter(d->d.incident().confidence()==Confidence.HIGH);
        if(detectionOpt.isPresent()){var d=detectionOpt.get();return diagnosedIncident(d.incident(),contextText,location,failure.rootCause().lineNumber(),allowLlm,chain,d.reasons(),matchConfidenceScorer.score(d,chain,contextText));}
        String lower=contextText.toLowerCase(Locale.ROOT);
        if(isConcurrencyFailure(lower))return manualReview(failure.rootCause().lineNumber(),location,"CONCURRENCY_FAILURE",IncidentCategory.THREADING,"Concurrency / data consistency failure","Concurrency / data consistency failure detected in application layer",contextText,chain,List.of("Matched protected concurrency fallback","Concurrency signature found in failure context"),matchConfidenceScorer.protectedFallback("Concurrency signature found in failure context"));
        if(isBusinessInvariantFailure(lower))return manualReview(failure.rootCause().lineNumber(),location,"BUSINESS_INVARIANT",IncidentCategory.BUSINESS,"Domain state machine violation","Domain state machine / business invariant violation",contextText,chain,List.of("Matched protected business-invariant fallback","IllegalStateException state/transition signature found"),matchConfidenceScorer.protectedFallback("IllegalStateException state/transition signature found"));
        String deepest=chain.isEmpty()?failure.rootCause().content():chain.get(chain.size()-1).evidence();
        return unknownFailure(contextText,lower,location,deepest,failure.rootCause().lineNumber(),allowLlm,chain,matchConfidenceScorer.unknown());
    }

    private DiagnosisResult diagnosedIncident(Incident incident,String evidence,String location,int line,boolean allowLlm,List<CauseChainAnalyzer.Cause> chain,List<String> reasons,MatchConfidenceScorer.Score score){
        incident.setEvidence(evidence);incident.setComponent(location);Set<FixType> allowed=FixPolicy.allowedFixes(incident.category());boolean human=allowed.contains(FixType.NO_AUTOMATIC_FIX);
        String fixType=human?FixType.NO_AUTOMATIC_FIX.name():formatFixTypes(allowed),fix=human?NO_AUTOMATIC_FIX:incident.recommendation(),llmAnalysis=!allowLlm||human?null:safelyExplainKnownIncident(incident);
        RemediationMetadata remediation=RemediationMetadata.from(incident,allowed);
        String diagnosis=incident.format()+System.lineSeparator()+formatCauseChain(chain)+formatMatchScore(score)+formatMatchReasons(reasons)+"FIX:"+System.lineSeparator()+fix+System.lineSeparator()+formatLlmSection(llmAnalysis);
        return new DiagnosisResult("DIAGNOSED",incident.type(),incident.category().name(),incident.severity().name(),incident.confidence().name(),incident.component(),incident.summary(),incident.rootCause(),incident.evidence(),fixType,fix,human,llmAnalysis!=null,line,diagnosis,chain,reasons,score.value(),score.band(),score.factors(),remediation);
    }
    private DiagnosisResult unknownFailure(String context,String lower,String location,String root,int line,boolean allowLlm,List<CauseChainAnalyzer.Cause> chain,MatchConfidenceScorer.Score score){
        IncidentCategory category=inferUnknownCategory(lower);String llmAnalysis=allowLlm?safelyAnalyzeUnknownLog(context,category):null;String fix=llmAnalysis==null?"No deterministic rule matched and local LLM analysis is unavailable. Human review required.":"Review the local LLM analysis and supporting evidence.";List<String> reasons=List.of("No deterministic rule matched the failure context");
        String diagnosis="Unknown failure detected at line "+line+System.lineSeparator()+context+System.lineSeparator()+formatCauseChain(chain)+formatMatchScore(score)+formatMatchReasons(reasons)+formatLlmSection(llmAnalysis);
        return new DiagnosisResult("UNKNOWN","UNKNOWN_FAILURE",category.name(),"UNKNOWN","LOW",location,"No deterministic rule matched this failure.",root,context,FixType.NO_AUTOMATIC_FIX.name(),fix,true,llmAnalysis!=null,line,diagnosis,chain,reasons,score.value(),score.band(),score.factors(),RemediationMetadata.from(category,Set.of(FixType.NO_AUTOMATIC_FIX)));
    }
    private DiagnosisResult manualReview(int line,String location,String type,IncidentCategory category,String summary,String root,String evidence,List<CauseChainAnalyzer.Cause> chain,List<String> reasons,MatchConfidenceScorer.Score score){
        String diagnosis="WHERE:"+System.lineSeparator()+location+System.lineSeparator()+System.lineSeparator()+"ROOT CAUSE:"+System.lineSeparator()+root+System.lineSeparator()+System.lineSeparator()+formatCauseChain(chain)+formatMatchScore(score)+formatMatchReasons(reasons)+"FIX:"+System.lineSeparator()+NO_AUTOMATIC_FIX+System.lineSeparator();
        return new DiagnosisResult("DIAGNOSED",type,category.name(),"HIGH","HIGH",location,summary,root,evidence,FixType.NO_AUTOMATIC_FIX.name(),NO_AUTOMATIC_FIX,true,false,line,diagnosis,chain,reasons,score.value(),score.band(),score.factors(),RemediationMetadata.from(category,Set.of(FixType.NO_AUTOMATIC_FIX)));
    }
    private String safelyExplainKnownIncident(Incident i){try{return normalizeLlmResponse(llm.explainKnownIncident(i));}catch(RuntimeException e){return null;}}
    private String safelyAnalyzeUnknownLog(String c,IncidentCategory cat){try{return normalizeLlmResponse(llm.analyzeUnknownLog(c,cat));}catch(RuntimeException e){return null;}}
    private static String normalizeLlmResponse(String r){return r==null||r.isBlank()?null:r.trim();}
    private static String formatLlmSection(String a){return a==null?"":System.lineSeparator()+"LLM ANALYSIS:"+System.lineSeparator()+a+System.lineSeparator();}
    private static String formatCauseChain(List<CauseChainAnalyzer.Cause> c){if(c==null||c.isEmpty())return "";String s=c.stream().map(x->"- line "+x.lineNumber()+": "+x.exceptionType()+(x.message().isBlank()?"":": "+x.message())).collect(Collectors.joining(System.lineSeparator()));return "CAUSE CHAIN:"+System.lineSeparator()+s+System.lineSeparator()+System.lineSeparator();}
    private static String formatMatchScore(MatchConfidenceScorer.Score s){if(s==null)return "";String f=s.factors().stream().map(x->"- "+x).collect(Collectors.joining(System.lineSeparator()));return "MATCH SCORE: "+s.value()+"/100 ("+s.band()+")"+System.lineSeparator()+(f.isBlank()?"":f+System.lineSeparator())+System.lineSeparator();}
    private static String formatMatchReasons(List<String> r){return r==null||r.isEmpty()?"":"WHY MATCHED:"+System.lineSeparator()+r.stream().map(x->"- "+x).collect(Collectors.joining(System.lineSeparator()))+System.lineSeparator()+System.lineSeparator();}
    private static String formatFixTypes(Set<FixType> a){return a.isEmpty()?"NONE":a.stream().map(Enum::name).sorted().collect(Collectors.joining(", "));}
    private static boolean isConcurrencyFailure(String l){return l.contains("optimisticlock")||l.contains("staleobjectstate")||l.contains("deadlock")||l.contains("could not serialize access");}
    private static boolean isBusinessInvariantFailure(String l){return l.contains("illegalstateexception")&&(l.contains("transition")||l.contains("state")||l.contains("not allowed"));}
    private static IncidentCategory inferUnknownCategory(String l){return l.contains("resttemplate")||l.contains("sockettimeoutexception")?IncidentCategory.INFRASTRUCTURE:IncidentCategory.UNKNOWN;}

    public record DiagnosisResult(String status,String type,String category,String severity,String confidence,String location,String summary,String rootCause,String evidence,String fixType,String fix,boolean humanReviewRequired,boolean llmUsed,Integer failureLine,String diagnosis,List<CauseChainAnalyzer.Cause> causeChain,List<String> matchReasons,int matchScore,String matchConfidence,List<String> matchScoreFactors,RemediationMetadata remediation){
        static DiagnosisResult empty(String message){return new DiagnosisResult("NO_FAILURE","NONE","NONE","NONE","NONE","—",message,"—","—","NONE","No remediation required.",false,false,null,message+System.lineSeparator(),List.of(),List.of(),0,"NONE",List.of(),null);}
    }
}
