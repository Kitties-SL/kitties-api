package es.kitti.adoption.service;

import es.kitti.adoption.intake.repository.IntakeRequestRepository;
import es.kitti.adoption.repository.AdoptionFormRepository;
import es.kitti.adoption.repository.AdoptionRequestFormRepository;
import es.kitti.adoption.repository.AdoptionRequestRepository;
import es.kitti.adoption.security.IdNumberEncryptionService;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class AdoptionAnonymizationWriteService {

    @Inject AdoptionRequestRepository     adoptionRequestRepository;
    @Inject AdoptionFormRepository        adoptionFormRepository;
    @Inject AdoptionRequestFormRepository adoptionRequestFormRepository;
    @Inject IntakeRequestRepository       intakeRequestRepository;
    @Inject IdNumberEncryptionService     encryptionService;

    @WithTransaction
    public Uni<Void> anonymizeUser(Long userId) {
        String placeholder = encryptionService.encrypt("SUPRIMIDO");
        return adoptionRequestRepository.findByAdopterId(userId)
                .onItem().transformToUni(requests -> {
                    List<Long> requestIds = requests.stream().map(r -> r.id).toList();
                    return adoptionFormRepository.anonymizeForRequestIds(requestIds, placeholder)
                            .chain(() -> adoptionRequestFormRepository.clearAllergiesForRequestIds(requestIds))
                            .chain(() -> intakeRequestRepository.anonymizeByUserId(userId));
                })
                .replaceWithVoid();
    }
}