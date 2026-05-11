package es.kitti.adoption.config;

import es.kitti.adoption.dto.AdoptionDataExport;
import es.kitti.adoption.dto.AdoptionExportEntry;
import es.kitti.adoption.dto.AdoptionFormCreateRequest;
import es.kitti.adoption.dto.AdoptionFormResponse;
import es.kitti.adoption.dto.AdoptionPipelineStatsResponse;
import es.kitti.adoption.dto.AdoptionRequestCreateRequest;
import es.kitti.adoption.dto.AdoptionRequestFormCreateRequest;
import es.kitti.adoption.dto.AdoptionRequestFormResponse;
import es.kitti.adoption.dto.AdoptionRequestResponse;
import es.kitti.adoption.dto.AdoptionStatusUpdateRequest;
import es.kitti.adoption.dto.ExpenseResponse;
import es.kitti.adoption.dto.InterviewCreateRequest;
import es.kitti.adoption.dto.InterviewResponse;
import es.kitti.adoption.entity.ActivityLevel;
import es.kitti.adoption.entity.AdoptionStatus;
import es.kitti.adoption.entity.ExpenseRecipient;
import es.kitti.adoption.entity.HousingType;
import es.kitti.adoption.event.AdoptionFormAnalysedEvent;
import es.kitti.adoption.event.AdoptionFormSubmittedEvent;
import es.kitti.adoption.intake.dto.IntakeDecisionRequest;
import es.kitti.adoption.intake.dto.IntakePipelineStatsResponse;
import es.kitti.adoption.intake.dto.IntakeRejectionResponse;
import es.kitti.adoption.intake.dto.IntakeRequestCreateRequest;
import es.kitti.adoption.intake.dto.IntakeRequestResponse;
import es.kitti.mon.error.ErrorResponse;
import es.kitti.adoption.intake.entity.IntakeStatus;
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
