package es.kitti.organization.repository;

import es.kitti.organization.geo.Coordinates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CityCoordinatesRepositoryTest {

    private CityCoordinatesRepository repo;

    @BeforeEach
    void setUp() {
        repo = new CityCoordinatesRepository();
        repo.onStart(null); // carga el CSV real del classpath
    }

    @Test
    void findByCity_exactName_resolves() {
        assertTrue(repo.findByCity("La Orotava").isPresent());
    }

    @Test
    void findByCity_isCaseInsensitive() {
        assertTrue(repo.findByCity("la orotava").isPresent());
        assertTrue(repo.findByCity("LA OROTAVA").isPresent());
    }

    @Test
    void findByCity_isAccentInsensitive() {
        assertEquals(repo.findByCity("Güímar"), repo.findByCity("Guimar"));
        assertTrue(repo.findByCity("guimar").isPresent());
    }

    @Test
    void findByCity_aliasLaLaguna_resolvesSameAsFullName() {
        Coordinates shortName = repo.findByCity("La Laguna").orElseThrow();
        Coordinates fullName = repo.findByCity("San Cristóbal de La Laguna").orElseThrow();
        assertEquals(fullName, shortName);
    }

    @Test
    void findByCity_unknown_isEmpty() {
        assertTrue(repo.findByCity("Madrid").isEmpty());
    }

    @Test
    void findByCity_nullOrBlank_isEmpty() {
        assertTrue(repo.findByCity(null).isEmpty());
        assertTrue(repo.findByCity("   ").isEmpty());
    }

    @Test
    void normalize_stripsAccentsCaseAndCollapsesWhitespace() {
        assertEquals("guimar", CityCoordinatesRepository.normalize("  GÜÍMAR  "));
        assertEquals("la orotava", CityCoordinatesRepository.normalize("La  Orotava"));
    }
}