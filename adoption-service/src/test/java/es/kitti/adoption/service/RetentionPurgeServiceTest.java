package es.kitti.adoption.service;

import es.kitti.adoption.entity.AdoptionRequest;
import es.kitti.adoption.repository.AdoptionFormRepository;
import es.kitti.adoption.repository.AdoptionRequestFormRepository;
import es.kitti.adoption.repository.AdoptionRequestRepository;
import es.kitti.adoption.security.IdNumberEncryptionService;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetentionPurgeServiceTest {

    @Mock AdoptionRequestRepository     adoptionRequestRepository;
    @Mock AdoptionFormRepository        adoptionFormRepository;
    @Mock AdoptionRequestFormRepository adoptionRequestFormRepository;
    @Mock IdNumberEncryptionService     encryptionService;
    @InjectMocks RetentionPurgeService service;

    @Test
    void purgeRejected_noItems_skipsDelete() {
        when(adoptionRequestRepository.findRejectedBefore(any(LocalDateTime.class)))
                .thenReturn(Uni.createFrom().item(List.of()));

        service.purgeRejected().await().indefinitely();

        verify(adoptionFormRepository, never()).deleteByRequestIds(any());
        verify(adoptionRequestRepository, never()).deleteByIds(any());
    }

    @Test
    void purgeRejected_withItems_cascadeDeletesInOrder() {
        AdoptionRequest req = new AdoptionRequest();
        req.id = 5L;
        when(adoptionRequestRepository.findRejectedBefore(any(LocalDateTime.class)))
                .thenReturn(Uni.createFrom().item(List.of(req)));
        when(adoptionFormRepository.deleteByRequestIds(List.of(5L)))
                .thenReturn(Uni.createFrom().item(1L));
        when(adoptionRequestFormRepository.deleteByRequestIds(List.of(5L)))
                .thenReturn(Uni.createFrom().item(1L));
        when(adoptionRequestRepository.deleteByIds(List.of(5L)))
                .thenReturn(Uni.createFrom().item(1L));

        service.purgeRejected().await().indefinitely();

        InOrder order = inOrder(adoptionFormRepository, adoptionRequestFormRepository, adoptionRequestRepository);
        order.verify(adoptionFormRepository).deleteByRequestIds(List.of(5L));
        order.verify(adoptionRequestFormRepository).deleteByRequestIds(List.of(5L));
        order.verify(adoptionRequestRepository).deleteByIds(List.of(5L));
    }

    @Test
    void anonymizeCompleted_noItems_skipsAnonymize() {
        when(encryptionService.encrypt("SUPRIMIDO")).thenReturn("enc");
        when(adoptionRequestRepository.findCompletedBefore(any(LocalDateTime.class)))
                .thenReturn(Uni.createFrom().item(List.of()));

        service.anonymizeCompleted().await().indefinitely();

        verify(adoptionFormRepository, never()).anonymizeForRequestIds(any(), any());
    }

    @Test
    void anonymizeCompleted_withItems_anonymizesInOrder() {
        when(encryptionService.encrypt("SUPRIMIDO")).thenReturn("enc");
        AdoptionRequest req = new AdoptionRequest();
        req.id = 7L;
        when(adoptionRequestRepository.findCompletedBefore(any(LocalDateTime.class)))
                .thenReturn(Uni.createFrom().item(List.of(req)));
        when(adoptionFormRepository.anonymizeForRequestIds(List.of(7L), "enc"))
                .thenReturn(Uni.createFrom().item(1));
        when(adoptionRequestFormRepository.clearAllergiesForRequestIds(List.of(7L)))
                .thenReturn(Uni.createFrom().item(1));

        service.anonymizeCompleted().await().indefinitely();

        InOrder order = inOrder(adoptionFormRepository, adoptionRequestFormRepository);
        order.verify(adoptionFormRepository).anonymizeForRequestIds(List.of(7L), "enc");
        order.verify(adoptionRequestFormRepository).clearAllergiesForRequestIds(List.of(7L));
    }
}
