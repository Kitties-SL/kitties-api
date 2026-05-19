package es.kitti.cat.repository;

import es.kitti.cat.entity.CatImage;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CatImageRepository implements PanacheRepository<CatImage> {

    public Multi<CatImage> findByCatId(Long catId) {
        return find("catId", catId).list()
                .onItem().transformToMulti(list -> Multi.createFrom().iterable(list));
    }

    public Uni<Void> deleteByCatId(Long catId) {
        return delete("catId", catId).replaceWithVoid();
    }
}