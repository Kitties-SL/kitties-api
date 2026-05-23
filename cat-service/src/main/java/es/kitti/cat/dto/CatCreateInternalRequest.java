package es.kitti.cat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.cat.domain.*;
import es.kitti.mon.either.Validation;

public record CatCreateInternalRequest(
        @JsonProperty("name")           String name,
        @JsonProperty("age")            Integer age,
        @JsonProperty("sex")            String sex,
        @JsonProperty("description")    String description,
        @JsonProperty("neutered")       Boolean neutered,
        @JsonProperty("city")           String city,
        @JsonProperty("region")         String region,
        @JsonProperty("country")        String country,
        @JsonProperty("latitude")       Double latitude,
        @JsonProperty("longitude")      Double longitude,
        @JsonProperty("organizationId") Long organizationId
) {
    public Validation<CatCreateInternalRequest> validate() {
        return Validation.valid(this)
                .and(Name.of("name", name))
                .and(CatAge.of(age))
                .and(Sex.of(sex))
                .and(City.of(city))
                .and(Country.of(country))
                .and(OrganizationId.of(organizationId));
    }
}
