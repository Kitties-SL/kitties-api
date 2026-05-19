package es.kitti.formanalysis.repository;

import es.kitti.formanalysis.entity.FormAnalysis;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FormAnalysisRepository implements PanacheRepository<FormAnalysis> {

    public Uni<FormAnalysis> findByAdoptionRequestId(Long adoptionRequestId) {
        return find("adoptionRequestId", adoptionRequestId).firstResult();
    }
}