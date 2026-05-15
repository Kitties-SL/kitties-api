package es.kitti.formanalysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import es.kitti.formanalysis.dto.llm.LlmTextAnalysis;
import es.kitti.formanalysis.entity.*;
import es.kitti.formanalysis.event.AdoptionFormAnalysedEvent;
import es.kitti.formanalysis.event.AdoptionFormSubmittedEvent;
import es.kitti.formanalysis.rules.FlagResult;
import es.kitti.formanalysis.rules.FormAnalysisRules;
import es.kitti.formanalysis.rules.LlmFlagConverter;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.quarkus.arc.Arc;
import io.smallrye.mutiny.infrastructure.Infrastructure;

import java.util.List;
import java.util.stream.Stream;

@ApplicationScoped
public class FormAnalysisService {

    @Inject
    FormAnalysisRules formAnalysisRules;

    @Inject
    FormAnalysisAiService formAnalysisAiService;

    @Inject
    LlmPromptBuilder llmPromptBuilder;

    @Inject
    LlmFlagConverter llmFlagConverter;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @Channel("adoption-form-analysed")
    Emitter<AdoptionFormAnalysedEvent> adoptionFormAnalysedEmitter;

    @Inject
    FormAnalysisPersistenceService persistenceService;

    @Incoming("adoption-form-submitted")
    public Uni<Void> onFormSubmitted(String message) {
        AdoptionFormSubmittedEvent event;
        try {
            event = objectMapper.readValue(message, AdoptionFormSubmittedEvent.class);
        } catch (Exception e) {
            Log.errorf("Error processing adoption-form-submitted, routing to DLQ: %s", e.getMessage());
            return Uni.createFrom().failure(e);
        }

        Log.infof("Analysing form for adoption request: %d", event.adoptionRequestId());

        List<FlagResult> rulesFlags = formAnalysisRules.evaluate(event);
        String prompt = llmPromptBuilder.build(event);

        return Uni.createFrom().item(() -> {
                    var rc = Arc.container().requestContext();
                    rc.activate();
                    try {
                        return formAnalysisAiService.analyzeTextFields(prompt);
                    } finally {
                        rc.deactivate();
                    }
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onFailure().recoverWithItem(e -> {
                    Log.warnf("LLM unavailable for request %d: %s", event.adoptionRequestId(), e.getMessage());
                    return null;
                })
                .onItem().transform(this::parseLlmResponse)
                .onItem().transform(r -> r != null ? r : LlmTextAnalysis.unavailable())
                .onItem().transformToUni(llmResult -> {
                    List<FlagResult> llmFlags = llmFlagConverter.convert(llmResult);
                    List<FlagResult> allFlags = Stream.concat(rulesFlags.stream(), llmFlags.stream()).toList();

                    long criticalCount = allFlags.stream()
                            .filter(f -> f.severity() == FlagSeverity.Critical).count();
                    long warningCount = allFlags.stream()
                            .filter(f -> f.severity() == FlagSeverity.Warning).count();
                    long noticeCount = allFlags.stream()
                            .filter(f -> f.severity() == FlagSeverity.Notice).count();

                    AnalysisDecision decision = determineDecision(criticalCount, warningCount);
                    String rejectionReason = buildRejectionReason(allFlags, decision);

                    FormAnalysis analysis = new FormAnalysis();
                    analysis.adoptionRequestId = event.adoptionRequestId();
                    analysis.decision = decision;
                    analysis.rejectionReason = rejectionReason;
                    analysis.criticalFlags = (int) criticalCount;
                    analysis.warningFlags = (int) warningCount;
                    analysis.noticeFlags = (int) noticeCount;
                    analysis.llmReasoning = llmResult.reasoning();

                    List<FormFlag> formFlags = allFlags.stream().map(f -> {
                        FormFlag flag = new FormFlag();
                        flag.severity = f.severity();
                        flag.code = f.code();
                        flag.description = f.description();
                        return flag;
                    }).toList();

                    return persistenceService.persist(analysis, formFlags)
                            .onItem().invoke(saved -> {
                                adoptionFormAnalysedEmitter.send(new AdoptionFormAnalysedEvent(
                                        event.adoptionRequestId(),
                                        decision.name(),
                                        rejectionReason,
                                        event.adopterId(),
                                        (int) criticalCount,
                                        (int) warningCount,
                                        (int) noticeCount
                                ));
                                Log.infof("Form analysis completed for request %d: %s",
                                        event.adoptionRequestId(), decision);
                            })
                            .replaceWithVoid();
                });
    }

    private LlmTextAnalysis parseLlmResponse(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, LlmTextAnalysis.class);
        } catch (Exception e) {
            Log.warnf("LLM returned unparseable JSON: %s", e.getMessage());
            return null;
        }
    }

    private AnalysisDecision determineDecision(long criticalCount, long warningCount) {
        if (criticalCount >= 1) return AnalysisDecision.Rejected;
        if (warningCount >= 3) return AnalysisDecision.Rejected;
        if (warningCount >= 1) return AnalysisDecision.ReviewRequired;
        return AnalysisDecision.Approved;
    }

    private String buildRejectionReason(List<FlagResult> flags, AnalysisDecision decision) {
        if (decision == AnalysisDecision.Approved) return null;

        return flags.stream()
                .filter(f -> f.severity() == FlagSeverity.Critical ||
                        f.severity() == FlagSeverity.Warning)
                .map(FlagResult::description)
                .reduce((a, b) -> a + ". " + b)
                .orElse(null);
    }
}
