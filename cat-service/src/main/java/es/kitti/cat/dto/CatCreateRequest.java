package es.kitti.cat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.cat.domain.CatAge;
import es.kitti.cat.domain.City;
import es.kitti.cat.domain.Country;
import es.kitti.cat.domain.Name;
import es.kitti.cat.domain.Sex;

public record CatCreateRequest(
        @JsonProperty("name")        String name,
        @JsonProperty("age")         Integer age,
        @JsonProperty("sex")         String sex,
        @JsonProperty("description") String description,
        @JsonProperty("neutered")    Boolean neutered,
        @JsonProperty("city")        String city,
        @JsonProperty("region")      String region,
        @JsonProperty("country")     String country,
        @JsonProperty("latitude")    Double latitude,
        @JsonProperty("longitude")   Double longitude
) {
    public Validation<CatCreateRequest> validate() {
        return Validation.valid(this)
                .and(Name.of("name", name))
                .and(CatAge.of(age))
                .and(Sex.of(sex))
                .and(City.of(city))
                .and(Country.of(country));
    }
}
