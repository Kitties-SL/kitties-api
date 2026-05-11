package es.kitti.cat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.cat.domain.CatAge;
import es.kitti.cat.domain.City;
import es.kitti.cat.domain.Country;
import es.kitti.cat.domain.Name;
import es.kitti.cat.entity.CatSex;

import java.util.List;

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
    private static final List<String> VALID_SEX_VALUES =
            List.of(CatSex.Male.name(), CatSex.Female.name());

    public Validation<CatCreateRequest> validate() {
        var result = Validation.<CatCreateRequest>valid(this);
        if (sex == null || sex.isBlank())
            result = result.and(Validation.invalidOne("sex", "REQUIRED"));
        else if (!VALID_SEX_VALUES.contains(sex))
            result = result.and(Validation.invalidOne("sex", "INVALID_VALUE"));
        return result
                .and(Name.of("name", name))
                .and(CatAge.of(age))
                .and(City.of(city))
                .and(Country.of(country));
    }
}
