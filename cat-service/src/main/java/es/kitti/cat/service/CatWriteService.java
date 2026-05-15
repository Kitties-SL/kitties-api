package es.kitti.cat.service;

import es.kitti.cat.entity.CatStatus;
import es.kitti.cat.repository.CatRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CatWriteService {

    @Inject
    CatRepository catRepository;

    @WithTransaction
    public Uni<Void> markDeleted(Long catId) {
        return catRepository.findById(catId)
                .onItem().transformToUni(cat -> {
                    cat.status = CatStatus.Deleted;
                    return catRepository.persist(cat).replaceWithVoid();
                });
    }
}
