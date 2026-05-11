package es.kitti.cat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.cat.domain.CatAge;
import es.kitti.cat.domain.City;
import es.kitti.cat.domain.Country;
import es.kitti.cat.domain.Name;
import es.kitti.cat.entity.CatSex;

import java.util.Arrays;

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
        var validSexValues = Arrays.stream(CatSex.values()).map(Enum::name).toList();
        var result = Validation.<CatCreateRequest>valid(this);
        if (sex == null || sex.isBlank())
            result = result.and(Validation.invalidOne("sex", "REQUIRED"));
        else if (!validSexValues.contains(sex))
            result = result.and(Validation.invalidOne("sex", "INVALID_VALUE"));
        return result
                .and(Name.of("name", name))
                .and(CatAge.of(age))
                .and(City.of(city))
                .and(Country.of(country));
    }
}
