package es.kitti.notification.service;

import es.kitti.notification.client.IpGeolocationClient;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class IpGeoLookupService {

    static final String UNKNOWN = "desconocida";
    static final String LOCAL   = "red local";

    @Inject
    @RestClient
    IpGeolocationClient client;

    public Uni<String> describe(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip))
            return Uni.createFrom().item(UNKNOWN);
        if (isLocalOrPrivate(ip))
            return Uni.createFrom().item(LOCAL);

        return client.lookup(ip)
                .onItem().transform(r -> format(r, ip))
                .onFailure().recoverWithItem(t -> {
                    Log.warnf("IP geolocation failed for %s: %s", ip, t.getMessage());
                    return ip;
                });
    }

    private static String format(IpGeolocationClient.GeoResponse r, String fallback) {
        if (r == null) return fallback;
        String city = blankToNull(r.cityName());
        String country = blankToNull(r.countryName());
        if (city == null && country == null) return fallback;
        if (city == null) return country;
        if (country == null) return city;
        return city + ", " + country;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static boolean isLocalOrPrivate(String ip) {
        if (ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1")
                || ip.equalsIgnoreCase("localhost")) return true;
        if (ip.startsWith("10.") || ip.startsWith("192.168.")) return true;
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) return true;
                } catch (NumberFormatException ignored) {}
            }
        }
        return false;
    }
}
