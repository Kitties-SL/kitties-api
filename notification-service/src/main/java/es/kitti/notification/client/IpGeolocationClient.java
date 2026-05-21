package es.kitti.notification.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.time.temporal.ChronoUnit;

@RegisterRestClient(configKey = "ip2location")
@Produces(MediaType.APPLICATION_JSON)
public interface IpGeolocationClient {

    @GET
    @Path("/")
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5,
                    delay = 60, delayUnit = ChronoUnit.SECONDS)
    Uni<GeoResponse> lookup(@QueryParam("ip") String ip);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeoResponse(
            @JsonProperty("country_name") String countryName,
            @JsonProperty("region_name")  String regionName,
            @JsonProperty("city_name")    String cityName
    ) {}
}
