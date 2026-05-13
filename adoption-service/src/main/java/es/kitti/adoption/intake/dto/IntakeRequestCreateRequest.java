package es.kitti.adoption.intake.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.adoption.domain.CatAge;
import es.kitti.adoption.domain.City;
import es.kitti.adoption.domain.Name;

public record IntakeRequestCreateRequest(
        @JsonProperty("targetOrganizationId") Long targetOrganizationId,
        @JsonProperty("catName")              String catName,
        @JsonProperty("catAge")               Integer catAge,
        @JsonProperty("region")               String region,
        @JsonProperty("city")                 String city,
        @JsonProperty("vaccinated")           Boolean vaccinated,
        @JsonProperty("description")          String description
) {
    public Validation<IntakeRequestCreateRequest> validate() {
        return Validation.valid(this)
                .required("targetOrganizationId", targetOrganizationId)
                .required("vaccinated",           vaccinated)
                .and(Name.of("catName", catName))
                .and(Name.of("region",  region))
                .and(City.of(city))
                .and(CatAge.of(catAge));
    }
}
