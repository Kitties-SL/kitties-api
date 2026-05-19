package es.kitti.formanalysis.config;

import es.kitti.formanalysis.client.*;
import es.kitti.formanalysis.dto.FormAnalysisDetailResponse;
import es.kitti.formanalysis.dto.FormFlagResponse;
import es.kitti.formanalysis.dto.llm.LlmTextAnalysis;
import es.kitti.formanalysis.dto.llm.StructuralAssessment;
import es.kitti.formanalysis.entity.AnalysisDecision;
import es.kitti.formanalysis.entity.FlagSeverity;
import es.kitti.formanalysis.event.AdoptionFormAnalysedEvent;
import es.kitti.formanalysis.event.AdoptionFormSubmittedEvent;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
        AdoptionFormAnalysedEvent.class,
        AdoptionFormSubmittedEvent.class,
        AnalysisDecision.class,
        FlagSeverity.class,
        LlmTextAnalysis.class,
        StructuralAssessment.class,
        FormAnalysisDetailResponse.class,
        FormFlagResponse.class,
        NimChatRequest.class,
        NimChatResponse.class,
        NimChoice.class,
        NimMessage.class,
        NimResponseFormat.class
})
public class NativeConfig {}
