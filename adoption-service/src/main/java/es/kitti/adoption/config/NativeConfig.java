package es.kitti.adoption.config;

import es.kitti.adoption.dto.*;
import es.kitti.adoption.entity.ActivityLevel;
import es.kitti.adoption.entity.AdoptionStatus;
import es.kitti.adoption.entity.ExpenseRecipient;
import es.kitti.adoption.entity.HousingType;
import es.kitti.adoption.event.AdoptionFormAnalysedEvent;
import es.kitti.adoption.event.AdoptionFormSubmittedEvent;
import es.kitti.adoption.intake.dto.*;
import es.kitti.adoption.intake.entity.IntakeStatus;
import es.kitti.mon.error.ErrorResponse;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
        AdoptionDataExport.class,
        AdoptionExportEntry.class,
        AdoptionFormCreateRequest.class,
        AdoptionFormResponse.class,
        AdoptionPipelineStatsResponse.class,
        AdoptionRequestCreateRequest.class,
        AdoptionRequestFormCreateRequest.class,
        AdoptionRequestFormResponse.class,
        AdoptionRequestResponse.class,
        AdoptionStatusUpdateRequest.class,
        ExpenseResponse.class,
        InterviewCreateRequest.class,
        InterviewResponse.class,
        AdoptionFormAnalysedEvent.class,
        AdoptionFormSubmittedEvent.class,
        IntakeDecisionRequest.class,
        IntakePipelineStatsResponse.class,
        IntakeRejectionResponse.class,
        IntakeRequestCreateRequest.class,
        IntakeRequestResponse.class,
        ActivityLevel.class,
        AdoptionStatus.class,
        ExpenseRecipient.class,
        HousingType.class,
        IntakeStatus.class,
        ErrorResponse.class
})
public class NativeConfig {}
