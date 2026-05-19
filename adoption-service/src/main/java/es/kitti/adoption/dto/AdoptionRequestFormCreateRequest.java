package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.adoption.entity.ActivityLevel;
import es.kitti.adoption.entity.HousingType;
import es.kitti.mon.either.Validation;

public record AdoptionRequestFormCreateRequest(
        @JsonProperty("hasPreviousCatExperience") Boolean hasPreviousCatExperience,
        @JsonProperty("previousPetsHistory")       String previousPetsHistory,
        @JsonProperty("adultsInHousehold")         Integer adultsInHousehold,
        @JsonProperty("hasChildren")               Boolean hasChildren,
        @JsonProperty("childrenAges")              String childrenAges,
        @JsonProperty("hasOtherPets")              Boolean hasOtherPets,
        @JsonProperty("otherPetsDescription")      String otherPetsDescription,
        @JsonProperty("hoursAlonePerDay")          Integer hoursAlonePerDay,
        @JsonProperty("stableHousing")             Boolean stableHousing,
        @JsonProperty("housingInstabilityReason")  String housingInstabilityReason,

        @JsonProperty("housingType")               HousingType housingType,
        @JsonProperty("housingSize")               Integer housingSize,
        @JsonProperty("hasOutdoorAccess")          Boolean hasOutdoorAccess,
        @JsonProperty("isRental")                  Boolean isRental,
        @JsonProperty("rentalPetsAllowed")         Boolean rentalPetsAllowed,
        @JsonProperty("hasWindowsWithView")        Boolean hasWindowsWithView,
        @JsonProperty("hasVerticalSpace")          Boolean hasVerticalSpace,
        @JsonProperty("hasHidingSpots")            Boolean hasHidingSpots,
        @JsonProperty("householdActivityLevel")    ActivityLevel householdActivityLevel,

        @JsonProperty("whyCatsNeedToPlay")            String whyCatsNeedToPlay,
        @JsonProperty("dailyPlayMinutes")             Integer dailyPlayMinutes,
        @JsonProperty("plannedEnrichment")            String plannedEnrichment,
        @JsonProperty("reactionToUnwantedBehavior")   String reactionToUnwantedBehavior,
        @JsonProperty("hasScratchingPost")            Boolean hasScratchingPost,
        @JsonProperty("willingToEnrichEnvironment")   Boolean willingToEnrichEnvironment,

        @JsonProperty("motivationToAdopt")             String motivationToAdopt,
        @JsonProperty("understandsLongTermCommitment") Boolean understandsLongTermCommitment,
        @JsonProperty("hasVetBudget")                  Boolean hasVetBudget,
        @JsonProperty("allHouseholdMembersAgree")      Boolean allHouseholdMembersAgree,
        @JsonProperty("anyoneHasAllergies")            Boolean anyoneHasAllergies,
        @JsonProperty("allergiesDetail")               String allergiesDetail
) {
    public Validation<AdoptionRequestFormCreateRequest> validate() {
        return Validation.valid(this)
                .required("hasPreviousCatExperience",      hasPreviousCatExperience)
                .and(adultsInHousehold(adultsInHousehold))
                .required("hasChildren",                   hasChildren)
                .required("hasOtherPets",                  hasOtherPets)
                .and(hoursAlonePerDay(hoursAlonePerDay))
                .required("stableHousing",                 stableHousing)
                .required("housingType",                   housingType)
                .and(housingSize(housingSize))
                .required("hasOutdoorAccess",              hasOutdoorAccess)
                .required("isRental",                      isRental)
                .required("hasWindowsWithView",            hasWindowsWithView)
                .required("hasVerticalSpace",              hasVerticalSpace)
                .required("hasHidingSpots",                hasHidingSpots)
                .required("householdActivityLevel",        householdActivityLevel)
                .requiredString("whyCatsNeedToPlay",       whyCatsNeedToPlay)
                .and(dailyPlayMinutes(dailyPlayMinutes))
                .requiredString("plannedEnrichment",       plannedEnrichment)
                .requiredString("reactionToUnwantedBehavior", reactionToUnwantedBehavior)
                .required("hasScratchingPost",             hasScratchingPost)
                .required("willingToEnrichEnvironment",    willingToEnrichEnvironment)
                .requiredString("motivationToAdopt",       motivationToAdopt)
                .required("understandsLongTermCommitment", understandsLongTermCommitment)
                .required("hasVetBudget",                  hasVetBudget)
                .required("allHouseholdMembersAgree",      allHouseholdMembersAgree)
                .required("anyoneHasAllergies",            anyoneHasAllergies);
    }

    private static Validation<?> adultsInHousehold(Integer v) {
        if (v == null) return Validation.invalidOne("adultsInHousehold", "REQUIRED");
        if (v < 1)     return Validation.invalidOne("adultsInHousehold", "TOO_SMALL");
        return Validation.valid(v);
    }

    private static Validation<?> hoursAlonePerDay(Integer v) {
        if (v == null)        return Validation.invalidOne("hoursAlonePerDay", "REQUIRED");
        if (v < 0 || v > 24) return Validation.invalidOne("hoursAlonePerDay", "INVALID_FORMAT");
        return Validation.valid(v);
    }

    private static Validation<?> housingSize(Integer v) {
        if (v == null) return Validation.invalidOne("housingSize", "REQUIRED");
        if (v < 1)     return Validation.invalidOne("housingSize", "TOO_SMALL");
        return Validation.valid(v);
    }

    private static Validation<?> dailyPlayMinutes(Integer v) {
        if (v == null) return Validation.invalidOne("dailyPlayMinutes", "REQUIRED");
        if (v < 0)     return Validation.invalidOne("dailyPlayMinutes", "TOO_SMALL");
        return Validation.valid(v);
    }
}
