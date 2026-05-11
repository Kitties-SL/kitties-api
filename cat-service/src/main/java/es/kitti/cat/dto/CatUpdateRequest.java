package es.kitti.cat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.cat.domain.CatAge;
import es.kitti.cat.domain.City;
import es.kitti.cat.domain.Country;
import es.kitti.cat.domain.Name;

public record CatUpdateRequest(
        @JsonProperty("name")        String name,
        @JsonProperty("age")         Integer age,
        @JsonProperty("description") String description,
        @JsonProperty("neutered")    Boolean neutered,
        @JsonProperty("city")        String city,
        @JsonProperty("region")      String region,
        @JsonProperty("country")     String country,
        @JsonProperty("latitude")    Double latitude,
        @JsonProperty("longitude")   Double longitude
) {
    public Validation<CatUpdateRequest> validate() {
        var result = Validation.<CatUpdateRequest>valid(this);
        if (name != null)    result = result.and(Name.of("name", name));
        if (age != null)     result = result.and(CatAge.of(age));
        if (city != null)    result = result.and(City.of(city));
        if (country != null) result = result.and(Country.of(country));
        return result;
    }
}
