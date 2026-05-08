package es.kitti.formanalysis.config;

import es.kitti.formanalysis.entity.AnalysisDecision;
import es.kitti.formanalysis.entity.FlagSeverity;
import es.kitti.formanalysis.event.AdoptionFormAnalysedEvent;
import es.kitti.formanalysis.event.AdoptionFormSubmittedEvent;
import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.FieldViolation;
import es.kitti.mon.error.ValidationError;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
        AdoptionFormAnalysedEvent.class,
        AdoptionFormSubmittedEvent.class,
        AnalysisDecision.class,
        FlagSeverity.class,
        ErrorResponse.class,
        FieldViolation.class,
        ValidationError.class
})
public class NativeConfig {}
