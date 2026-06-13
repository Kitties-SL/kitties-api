package es.kitti.organization.repository;

import es.kitti.organization.entity.Organization;
import es.kitti.organization.entity.OrganizationStatus;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.panache.common.Parameters;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class OrganizationRepository implements PanacheRepository<Organization> {

    public Uni<Boolean> existsByName(String name) {
        return count("name", name).onItem().transform(c -> c > 0);
    }

    public Uni<List<Organization>> findActiveByRegion(String region) {
        return list("region = ?1 and status = ?2 order by name asc",
                region, OrganizationStatus.Active);
    }

    public Uni<List<Organization>> findAllActive() {
        return list("status", OrganizationStatus.Active);
    }

    public Uni<List<Organization>> search(String name, String region, String city, int page, int size) {
        QueryFilter qf = buildActiveFilter(name, region, city);
        return find(qf.hql + " order by name asc", qf.params).page(page, size).list();
    }

    public Uni<Long> countSearch(String name, String region, String city) {
        QueryFilter qf = buildActiveFilter(name, region, city);
        return count(qf.hql, qf.params);
    }

    private QueryFilter buildActiveFilter(String name, String region, String city) {
        StringBuilder hql = new StringBuilder("status = :status");
        Parameters params = Parameters.with("status", OrganizationStatus.Active);
        if (name != null && !name.isBlank()) {
            hql.append(" and lower(name) like lower(:name)");
            params.and("name", "%" + name + "%");
        }
        if (region != null && !region.isBlank()) {
            hql.append(" and lower(region) = lower(:region)");
            params.and("region", region);
        }
        if (city != null && !city.isBlank()) {
            hql.append(" and lower(city) like lower(:city)");
            params.and("city", "%" + city + "%");
        }
        return new QueryFilter(hql.toString(), params);
    }

    private record QueryFilter(String hql, Parameters params) {}
}
