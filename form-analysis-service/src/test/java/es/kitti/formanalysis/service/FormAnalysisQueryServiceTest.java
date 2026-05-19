package es.kitti.formanalysis.service;

import es.kitti.formanalysis.entity.AnalysisDecision;
import es.kitti.formanalysis.entity.FlagSeverity;
import es.kitti.formanalysis.entity.FormAnalysis;
import es.kitti.formanalysis.entity.FormFlag;
import es.kitti.formanalysis.repository.FormAnalysisRepository;
import es.kitti.formanalysis.repository.FormFlagRepository;
import es.kitti.mon.error.ForbiddenError;
import es.kitti.mon.error.NotFoundError;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormAnalysisQueryServiceTest {

    @Mock
    FormAnalysisRepository formAnalysisRepository;

    @Mock
    FormFlagRepository formFlagRepository;

    @InjectMocks
    FormAnalysisQueryService queryService;

    @Test
    void findByAdoptionRequestId_analysisFound_returnsDetailWithFlags() {
        var analysis = analysis(1L, 10L, 3L);
        var flag = flag(1L, FlagSeverity.Warning, "UNSTABLE_HOUSING", "Mudanza prevista");

        when(formAnalysisRepository.findByAdoptionRequestId(10L))
                .thenReturn(Uni.createFrom().item(analysis));
        when(formFlagRepository.findByFormAnalysisId(1L))
                .thenReturn(Uni.createFrom().item(List.of(flag)));

        var result = queryService.findByAdoptionRequestId(10L, 3L).await().indefinitely();

        assertTrue(result.isRight());
        var detail = result.getOrElse(null);
        assertNotNull(detail);
        assertEquals(1L, detail.id());
        assertEquals(10L, detail.adoptionRequestId());
        assertEquals(3L, detail.organizationId());
        assertEquals(AnalysisDecision.ReviewRequired, detail.decision());
        assertEquals(1, detail.warningFlags());
        assertEquals(1, detail.flags().size());
        assertEquals("UNSTABLE_HOUSING", detail.flags().get(0).code());
        assertEquals(FlagSeverity.Warning, detail.flags().get(0).severity());
    }

    @Test
    void findByAdoptionRequestId_analysisNotFound_returnsNotFoundError() {
        when(formAnalysisRepository.findByAdoptionRequestId(99L))
                .thenReturn(Uni.createFrom().nullItem());

        var result = queryService.findByAdoptionRequestId(99L, 3L).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(404, (int) result.fold(e -> e.httpStatus(), __ -> 0));
        assertInstanceOf(NotFoundError.class, ((es.kitti.mon.either.Either.Left<?, ?>) result).value());
    }

    @Test
    void findByAdoptionRequestId_orgMismatch_returnsForbiddenError() {
        var analysis = analysis(1L, 10L, 3L);

        when(formAnalysisRepository.findByAdoptionRequestId(10L))
                .thenReturn(Uni.createFrom().item(analysis));

        var result = queryService.findByAdoptionRequestId(10L, 99L).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(403, (int) result.fold(e -> e.httpStatus(), __ -> 0));
        assertInstanceOf(ForbiddenError.class, ((es.kitti.mon.either.Either.Left<?, ?>) result).value());
    }

    @Test
    void findByAdoptionRequestId_noFlags_returnsEmptyFlagsList() {
        var analysis = analysis(1L, 10L, 3L);

        when(formAnalysisRepository.findByAdoptionRequestId(10L))
                .thenReturn(Uni.createFrom().item(analysis));
        when(formFlagRepository.findByFormAnalysisId(1L))
                .thenReturn(Uni.createFrom().item(List.of()));

        var result = queryService.findByAdoptionRequestId(10L, 3L).await().indefinitely();

        assertTrue(result.isRight());
        assertTrue(result.getOrElse(null).flags().isEmpty());
    }

    private static FormAnalysis analysis(Long id, Long adoptionRequestId, Long organizationId) {
        var a = new FormAnalysis();
        a.id = id;
        a.adoptionRequestId = adoptionRequestId;
        a.organizationId = organizationId;
        a.decision = AnalysisDecision.ReviewRequired;
        a.criticalFlags = 0;
        a.warningFlags = 1;
        a.noticeFlags = 0;
        a.llmReasoning = "Razonamiento de prueba";
        a.createdAt = LocalDateTime.now();
        return a;
    }

    private static FormFlag flag(Long id, FlagSeverity severity, String code, String description) {
        var f = new FormFlag();
        f.id = id;
        f.severity = severity;
        f.code = code;
        f.description = description;
        return f;
    }
}
