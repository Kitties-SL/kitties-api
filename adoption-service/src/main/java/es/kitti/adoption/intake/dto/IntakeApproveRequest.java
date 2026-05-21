package es.kitti.adoption.intake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.adoption.domain.Country;
import es.kitti.adoption.domain.Sex;
import es.kitti.mon.either.Validation;

public record IntakeApproveRequest(
        @JsonProperty("sex")         String sex,
        @JsonProperty("country")     String country,
        @JsonProperty("name")        String name,
        @JsonProperty("age")         Integer age,
        @JsonProperty("region")      String region,
        @JsonProperty("city")        String city,
        @JsonProperty("description") String description,
        @JsonProperty("neutered")    Boolean neutered
) {
    public Validation<IntakeApproveRequest> validate() {
        return Validation.valid(this)
                .and(Sex.of(sex))
                .and(Country.of(country));
    }
}
