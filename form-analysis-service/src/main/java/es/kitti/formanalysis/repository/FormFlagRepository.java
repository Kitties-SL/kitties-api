package es.kitti.formanalysis.repository;

import es.kitti.formanalysis.entity.FormFlag;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class FormFlagRepository implements PanacheRepository<FormFlag> {

    public Uni<List<FormFlag>> findByFormAnalysisId(Long formAnalysisId) {
        return list("formAnalysisId", formAnalysisId);
    }
}