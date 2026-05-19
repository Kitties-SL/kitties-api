package es.kitti.cat.repository;

import es.kitti.cat.entity.Cat;
import es.kitti.cat.entity.CatStatus;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class CatRepository implements PanacheRepository<Cat> {

    @WithSession
    public Uni<List<Cat>> findAvailable(int page, int size) {
        return find("status", CatStatus.Available).page(page, size).list();
    }

    @WithSession
    public Uni<Long> countAvailable() {
        return count("status", CatStatus.Available);
    }

    @WithSession
    public Uni<List<Cat>> findByCity(String city, int page, int size) {
        return find("status = ?1 and lower(city) like lower(?2)",
                CatStatus.Available, "%" + city + "%").page(page, size).list();
    }

    @WithSession
    public Uni<Long> countByCity(String city) {
        return count("status = ?1 and lower(city) like lower(?2)",
                CatStatus.Available, "%" + city + "%");
    }

    @WithSession
    public Uni<List<Cat>> findByName(String name, int page, int size) {
        return find("status = ?1 and lower(name) like lower(?2)",
                CatStatus.Available, "%" + name + "%").page(page, size).list();
    }

    @WithSession
    public Uni<Long> countByName(String name) {
        return count("status = ?1 and lower(name) like lower(?2)",
                CatStatus.Available, "%" + name + "%");
    }

    @WithSession
    public Uni<List<Cat>> findByCityAndName(String city, String name, int page, int size) {
        return find("status = ?1 and lower(city) like lower(?2) and lower(name) like lower(?3)",
                CatStatus.Available, "%" + city + "%", "%" + name + "%").page(page, size).list();
    }

    @WithSession
    public Uni<Long> countByCityAndName(String city, String name) {
        return count("status = ?1 and lower(city) like lower(?2) and lower(name) like lower(?3)",
                CatStatus.Available, "%" + city + "%", "%" + name + "%");
    }

    @WithSession
    public Uni<List<Cat>> findByOrganizationId(Long organizationId) {
        return find("organizationId = ?1 and status != ?2", organizationId, CatStatus.Deleted).list();
    }

    @WithSession
    public Uni<List<Cat>> findAllByOrganizationId(Long organizationId) {
        return find("organizationId", organizationId).list();
    }
}