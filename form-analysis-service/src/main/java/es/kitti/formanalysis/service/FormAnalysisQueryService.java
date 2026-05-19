package es.kitti.formanalysis.service;

import es.kitti.formanalysis.dto.FormAnalysisDetailResponse;
import es.kitti.formanalysis.dto.FormFlagResponse;
import es.kitti.formanalysis.repository.FormAnalysisRepository;
import es.kitti.formanalysis.repository.FormFlagRepository;
import es.kitti.mon.either.Either;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.ForbiddenError;
import es.kitti.mon.error.NotFoundError;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class FormAnalysisQueryService {

    @Inject
    FormAnalysisRepository formAnalysisRepository;

    @Inject
    FormFlagRepository formFlagRepository;

    @WithSession
    public Uni<Either<DomainError, FormAnalysisDetailResponse>> findByAdoptionRequestId(
            Long adoptionRequestId, Long requestingOrgId) {

        return formAnalysisRepository.findByAdoptionRequestId(adoptionRequestId)
                .onItem().transformToUni(analysis -> {
                    if (analysis == null)
                        return Uni.createFrom().item(Either.left(new NotFoundError("FORM_ANALYSIS_NOT_FOUND")));
                    if (!requestingOrgId.equals(analysis.organizationId))
                        return Uni.createFrom().item(Either.left(new ForbiddenError("FORM_ANALYSIS_FORBIDDEN")));

                    return formFlagRepository.findByFormAnalysisId(analysis.id)
                            .onItem().transform(flags -> {
                                var flagResponses = flags.stream()
                                        .map(f -> new FormFlagResponse(f.id, f.severity, f.code, f.description))
                                        .toList();
                                return Either.<DomainError, FormAnalysisDetailResponse>right(
                                        new FormAnalysisDetailResponse(
                                                analysis.id,
                                                analysis.adoptionRequestId,
                                                analysis.organizationId,
                                                analysis.decision,
                                                analysis.rejectionReason,
                                                analysis.criticalFlags,
                                                analysis.warningFlags,
                                                analysis.noticeFlags,
                                                analysis.llmReasoning,
                                                analysis.createdAt,
                                                flagResponses
                                        )
                                );
                            });
                });
    }
}
