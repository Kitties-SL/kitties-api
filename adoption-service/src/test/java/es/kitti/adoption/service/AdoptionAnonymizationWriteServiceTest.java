package es.kitti.adoption.service;

import es.kitti.adoption.entity.AdoptionRequest;
import es.kitti.adoption.intake.repository.IntakeRequestRepository;
import es.kitti.adoption.repository.AdoptionFormRepository;
import es.kitti.adoption.repository.AdoptionRequestFormRepository;
import es.kitti.adoption.repository.AdoptionRequestRepository;
import es.kitti.adoption.security.IdNumberEncryptionService;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdoptionAnonymizationWriteServiceTest {

    @Mock AdoptionRequestRepository     adoptionRequestRepository;
    @Mock AdoptionFormRepository        adoptionFormRepository;
    @Mock AdoptionRequestFormRepository adoptionRequestFormRepository;
    @Mock IntakeRequestRepository       intakeRequestRepository;
    @Mock IdNumberEncryptionService     encryptionService;
    @InjectMocks AdoptionAnonymizationWriteService service;

    @BeforeEach
    void setUp() {
        when(encryptionService.encrypt("SUPRIMIDO")).thenReturn("enc-placeholder");
    }

    @Test
    void anonymizeUser_withRequests_callsAllReposInSequence() {
        AdoptionRequest req = new AdoptionRequest();
        req.id = 1L;
        when(adoptionRequestRepository.findByAdopterId(42L))
                .thenReturn(Uni.createFrom().item(List.of(req)));
        when(adoptionFormRepository.anonymizeForRequestIds(List.of(1L), "enc-placeholder"))
                .thenReturn(Uni.createFrom().item(1));
        when(adoptionRequestFormRepository.clearAllergiesForRequestIds(List.of(1L)))
                .thenReturn(Uni.createFrom().item(1));
        when(intakeRequestRepository.anonymizeByUserId(42L))
                .thenReturn(Uni.createFrom().item(1));

        service.anonymizeUser(42L).await().indefinitely();

        InOrder order = inOrder(adoptionFormRepository, adoptionRequestFormRepository, intakeRequestRepository);
        order.verify(adoptionFormRepository).anonymizeForRequestIds(List.of(1L), "enc-placeholder");
        order.verify(adoptionRequestFormRepository).clearAllergiesForRequestIds(List.of(1L));
        order.verify(intakeRequestRepository).anonymizeByUserId(42L);
    }

    @Test
    void anonymizeUser_withNoRequests_stillCallsIntakeRepo() {
        when(adoptionRequestRepository.findByAdopterId(42L))
                .thenReturn(Uni.createFrom().item(List.of()));
        when(adoptionFormRepository.anonymizeForRequestIds(List.of(), "enc-placeholder"))
                .thenReturn(Uni.createFrom().item(0));
        when(adoptionRequestFormRepository.clearAllergiesForRequestIds(List.of()))
                .thenReturn(Uni.createFrom().item(0));
        when(intakeRequestRepository.anonymizeByUserId(42L))
                .thenReturn(Uni.createFrom().item(0));

        service.anonymizeUser(42L).await().indefinitely();

        verify(intakeRequestRepository).anonymizeByUserId(42L);
    }
}
