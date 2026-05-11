package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.adoption.domain.Address;
import es.kitti.adoption.domain.City;
import es.kitti.adoption.domain.IdNumber;
import es.kitti.adoption.domain.Name;
import es.kitti.adoption.domain.Phone;
import es.kitti.adoption.domain.PostalCode;

public record AdoptionFormCreateRequest(
        @JsonProperty("fullName")                  String fullName,
        @JsonProperty("idNumber")                  String idNumber,
        @JsonProperty("phone")                     String phone,
        @JsonProperty("address")                   String address,
        @JsonProperty("city")                      String city,
        @JsonProperty("postalCode")                String postalCode,
        @JsonProperty("acceptsVetVisits")          Boolean acceptsVetVisits,
        @JsonProperty("acceptsFollowUpContact")    Boolean acceptsFollowUpContact,
        @JsonProperty("acceptsReturnIfNeeded")     Boolean acceptsReturnIfNeeded,
        @JsonProperty("acceptsTermsAndConditions") Boolean acceptsTermsAndConditions,
        @JsonProperty("consentHealthData")         Boolean consentHealthData,
        @JsonProperty("additionalNotes")           String additionalNotes
) {
    public Validation<AdoptionFormCreateRequest> validate() {
        var result = Validation.<AdoptionFormCreateRequest>valid(this);
        if (acceptsVetVisits == null)          result = result.and(Validation.invalidOne("acceptsVetVisits",          "REQUIRED"));
        if (acceptsFollowUpContact == null)    result = result.and(Validation.invalidOne("acceptsFollowUpContact",    "REQUIRED"));
        if (acceptsReturnIfNeeded == null)     result = result.and(Validation.invalidOne("acceptsReturnIfNeeded",     "REQUIRED"));
        if (acceptsTermsAndConditions == null) result = result.and(Validation.invalidOne("acceptsTermsAndConditions", "REQUIRED"));
        if (consentHealthData == null)         result = result.and(Validation.invalidOne("consentHealthData",         "REQUIRED"));
        return result
                .and(Name.of("fullName", fullName))
                .and(IdNumber.of(idNumber))
                .and(Phone.of(phone))
                .and(Address.of(address))
                .and(City.of(city))
                .and(PostalCode.of(postalCode));
    }
}
