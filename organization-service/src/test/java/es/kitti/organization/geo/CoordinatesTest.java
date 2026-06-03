package es.kitti.organization.geo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoordinatesTest {

    @Test
    void distanceKm_samePoint_isZero() {
        var p = new Coordinates(28.3906, -16.5236);
        assertEquals(0.0, p.distanceKm(p), 0.0001);
    }

    @Test
    void distanceKm_knownNeighbours_isSmall() {
        // La Orotava ↔ Los Realejos: ~6 km en línea recta
        var orotava = new Coordinates(28.3906, -16.5236);
        var realejos = new Coordinates(28.3833, -16.5833);
        double d = orotava.distanceKm(realejos);
        assertTrue(d > 3 && d < 9, "esperado ~6km, fue " + d);
    }

    @Test
    void distanceKm_acrossIsland_isLarge() {
        // Santa Cruz ↔ Adeje: ~60 km
        var santaCruz = new Coordinates(28.4636, -16.2518);
        var adeje = new Coordinates(28.1227, -16.7260);
        double d = santaCruz.distanceKm(adeje);
        assertTrue(d > 50 && d < 80, "esperado ~60km, fue " + d);
    }

    @Test
    void distanceKm_isSymmetric() {
        var a = new Coordinates(28.4636, -16.2518);
        var b = new Coordinates(28.1227, -16.7260);
        assertEquals(a.distanceKm(b), b.distanceKm(a), 0.0001);
    }
}