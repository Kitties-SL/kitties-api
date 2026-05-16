package es.kitti.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;

import java.time.LocalDate;

public record RegisterOrganizationRequest(
        @JsonProperty("name")          String name,
        @JsonProperty("description")   String description,
        @JsonProperty("address")       String address,
        @JsonProperty("city")          String city,
        @JsonProperty("region")        String region,
        @JsonProperty("country")       String country,
        @JsonProperty("phone")         String phone,
        @JsonProperty("email")         String email,
        @JsonProperty("logoUrl")       String logoUrl,
        @JsonProperty("adminEmail")    String adminEmail,
        @JsonProperty("adminPassword") String adminPassword,
        @JsonProperty("adminName")     String adminName,
        @JsonProperty("adminSurname")  String adminSurname,
        @JsonProperty("adminBirthdate") LocalDate adminBirthdate
) {
    public Validation<RegisterOrganizationRequest> validate() {
        return Validation.valid(this)
                .requiredString("name", name)
                .requiredString("adminEmail", adminEmail)
                .requiredString("adminPassword", adminPassword)
                .and(adminPassword != null && !adminPassword.isBlank() && adminPassword.length() < 8
                        ? Validation.invalidOne("adminPassword", "INVALID_SIZE")
                        : Validation.valid(this))
                .requiredString("adminName", adminName)
                .requiredString("adminSurname", adminSurname);
    }
}
