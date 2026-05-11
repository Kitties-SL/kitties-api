package es.kitti.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;

public record CreateOrganizationRequest(
        @JsonProperty("name")        String name,
        @JsonProperty("description") String description,
        @JsonProperty("address")     String address,
        @JsonProperty("city")        String city,
        @JsonProperty("region")      String region,
        @JsonProperty("country")     String country,
        @JsonProperty("phone")       String phone,
        @JsonProperty("email")       String email,
        @JsonProperty("logoUrl")     String logoUrl
) {
    public Validation<CreateOrganizationRequest> validate() {
        var r = Validation.<CreateOrganizationRequest>valid(this);
        if (name == null || name.isBlank()) r = r.and(Validation.invalidOne("name", "REQUIRED"));
        return r;
    }
}
