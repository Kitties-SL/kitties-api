package es.kitti.organization.repository;

import es.kitti.organization.geo.Coordinates;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Chapuza pragmática: diccionario estático ciudad → coordenadas, hoy solo Tenerife.
 * Cuando llegue tráfico de fuera de la isla, se sustituye por un servicio de geocoding
 * sin tocar a los llamantes (la firma findByCity se mantiene).
 */
@ApplicationScoped
public class CityCoordinatesRepository {

    private static final String RESOURCE = "geo/tenerife-cities.csv";

    private final Map<String, Coordinates> byCity = new HashMap<>();

    void onStart(@Observes StartupEvent event) {
        load();
    }

    private void load() {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE);
        if (in == null) throw new IllegalStateException("City coordinates resource not found: " + RESOURCE);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                if (header) { header = false; continue; }
                String[] parts = line.split(",");
                if (parts.length < 3) continue;
                byCity.put(normalize(parts[0]), new Coordinates(
                        Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim())));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load city coordinates from " + RESOURCE, e);
        }
    }

    public Optional<Coordinates> findByCity(String city) {
        if (city == null || city.isBlank()) return Optional.empty();
        return Optional.ofNullable(byCity.get(normalize(city)));
    }

    static String normalize(String value) {
        return Normalizer.normalize(value.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("\\s+", " ");
    }
}