package es.kitti.formanalysis.config;

import es.kitti.formanalysis.client.NimChatRequest;
import es.kitti.formanalysis.client.NimChatResponse;
import es.kitti.formanalysis.client.NimChoice;
import es.kitti.formanalysis.client.NimMessage;
import es.kitti.formanalysis.client.NimResponseFormat;
import es.kitti.formanalysis.dto.llm.LlmTextAnalysis;
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
        NimChatRequest.class,
        NimChatResponse.class,
        NimChoice.class,
        NimMessage.class,
        NimResponseFormat.class
})
public class NativeConfig {}
