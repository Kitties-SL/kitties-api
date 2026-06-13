package es.kitti.organization.service;

import es.kitti.mon.error.DomainError;
import es.kitti.organization.dto.NearbyOrganizationResponse;
import es.kitti.organization.entity.Organization;
import es.kitti.organization.entity.OrganizationStatus;
import es.kitti.organization.geo.Coordinates;
import es.kitti.organization.mapper.OrganizationMapper;
import es.kitti.organization.repository.CityCoordinatesRepository;
import es.kitti.organization.repository.OrganizationRepository;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceNearbyTest {

    @Mock OrganizationRepository organizationRepository;
    @Mock CityCoordinatesRepository cityCoordinatesRepository;
    @Spy  OrganizationMapper mapper;
    @InjectMocks OrganizationService service;

    private Organization orgIn(long id, String city) {
        Organization o = new Organization();
        o.id = id;
        o.name = "Org " + id;
        o.city = city;
        o.region = "Santa Cruz de Tenerife";
        o.status = OrganizationStatus.Active;
        return o;
    }

    @Test
    void findNearby_ranksByDistanceAscending() {
        Organization near = orgIn(1, "Near");
        Organization mid = orgIn(2, "Mid");
        Organization far = orgIn(3, "Far");
        when(organizationRepository.findAllActive())
                .thenReturn(Uni.createFrom().item(List.of(far, near, mid)));
        when(cityCoordinatesRepository.findByCity("Near")).thenReturn(Optional.of(new Coordinates(28.40, -16.52)));
        when(cityCoordinatesRepository.findByCity("Mid")).thenReturn(Optional.of(new Coordinates(28.45, -16.40)));
        when(cityCoordinatesRepository.findByCity("Far")).thenReturn(Optional.of(new Coordinates(28.12, -16.72)));

        var result = service.findNearby(28.39, -16.52, null, 10).await().indefinitely();

        assertTrue(result.isRight());
        var ids = result.getOrElse(null).stream().map(NearbyOrganizationResponse::id).toList();
        assertEquals(List.of(1L, 2L, 3L), ids);
    }

    @Test
    void findNearby_respectsLimit() {
        Organization a = orgIn(1, "A");
        Organization b = orgIn(2, "B");
        Organization c = orgIn(3, "C");
        when(organizationRepository.findAllActive())
                .thenReturn(Uni.createFrom().item(List.of(a, b, c)));
        when(cityCoordinatesRepository.findByCity("A")).thenReturn(Optional.of(new Coordinates(28.40, -16.52)));
        when(cityCoordinatesRepository.findByCity("B")).thenReturn(Optional.of(new Coordinates(28.45, -16.40)));
        when(cityCoordinatesRepository.findByCity("C")).thenReturn(Optional.of(new Coordinates(28.12, -16.72)));

        var result = service.findNearby(28.39, -16.52, null, 2).await().indefinitely();

        assertEquals(2, result.getOrElse(null).size());
    }

    @Test
    void findNearby_excludesOrgsWithUnknownCity() {
        Organization known = orgIn(1, "Known");
        Organization unknown = orgIn(2, "Nowhere");
        when(organizationRepository.findAllActive())
                .thenReturn(Uni.createFrom().item(List.of(known, unknown)));
        when(cityCoordinatesRepository.findByCity("Known")).thenReturn(Optional.of(new Coordinates(28.40, -16.52)));
        when(cityCoordinatesRepository.findByCity("Nowhere")).thenReturn(Optional.empty());

        var result = service.findNearby(28.39, -16.52, null, 10).await().indefinitely();

        var ids = result.getOrElse(null).stream().map(NearbyOrganizationResponse::id).toList();
        assertEquals(List.of(1L), ids);
    }

    @Test
    void findNearby_withCityFallback_resolvesOrigin() {
        Organization a = orgIn(1, "A");
        when(organizationRepository.findAllActive())
                .thenReturn(Uni.createFrom().item(List.of(a)));
        when(cityCoordinatesRepository.findByCity("La Orotava")).thenReturn(Optional.of(new Coordinates(28.39, -16.52)));
        when(cityCoordinatesRepository.findByCity("A")).thenReturn(Optional.of(new Coordinates(28.40, -16.52)));

        var result = service.findNearby(null, null, "La Orotava", 10).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(1, result.getOrElse(null).size());
    }

    @Test
    void findNearby_unknownCity_returnsBadRequest() {
        when(cityCoordinatesRepository.findByCity("Atlantis")).thenReturn(Optional.empty());

        var result = service.findNearby(null, null, "Atlantis", 10).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(400, result.fold(DomainError::httpStatus, __ -> 0));
        verify(organizationRepository, never()).findAllActive();
    }

    @Test
    void findNearby_noOrigin_returnsBadRequest() {
        var result = service.findNearby(null, null, null, 10).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(400, result.fold(DomainError::httpStatus, __ -> 0));
        verify(organizationRepository, never()).findAllActive();
    }
}
