package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.adoption.entity.ActivityLevel;
import es.kitti.adoption.entity.HousingType;

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
        var r = Validation.<AdoptionRequestFormCreateRequest>valid(this);

        if (hasPreviousCatExperience == null)      r = r.and(Validation.invalidOne("hasPreviousCatExperience", "REQUIRED"));
        if (adultsInHousehold == null)             r = r.and(Validation.invalidOne("adultsInHousehold",        "REQUIRED"));
        else if (adultsInHousehold < 1)            r = r.and(Validation.invalidOne("adultsInHousehold",        "TOO_SMALL"));
        if (hasChildren == null)                   r = r.and(Validation.invalidOne("hasChildren",              "REQUIRED"));
        if (hasOtherPets == null)                  r = r.and(Validation.invalidOne("hasOtherPets",             "REQUIRED"));
        if (hoursAlonePerDay == null)              r = r.and(Validation.invalidOne("hoursAlonePerDay",         "REQUIRED"));
        else if (hoursAlonePerDay < 0 || hoursAlonePerDay > 24) r = r.and(Validation.invalidOne("hoursAlonePerDay", "INVALID_FORMAT"));
        if (stableHousing == null)                 r = r.and(Validation.invalidOne("stableHousing",            "REQUIRED"));

        if (housingType == null)                   r = r.and(Validation.invalidOne("housingType",              "REQUIRED"));
        if (housingSize == null)                   r = r.and(Validation.invalidOne("housingSize",              "REQUIRED"));
        else if (housingSize < 1)                  r = r.and(Validation.invalidOne("housingSize",              "TOO_SMALL"));
        if (hasOutdoorAccess == null)              r = r.and(Validation.invalidOne("hasOutdoorAccess",         "REQUIRED"));
        if (isRental == null)                      r = r.and(Validation.invalidOne("isRental",                 "REQUIRED"));
        if (hasWindowsWithView == null)            r = r.and(Validation.invalidOne("hasWindowsWithView",       "REQUIRED"));
        if (hasVerticalSpace == null)              r = r.and(Validation.invalidOne("hasVerticalSpace",         "REQUIRED"));
        if (hasHidingSpots == null)                r = r.and(Validation.invalidOne("hasHidingSpots",           "REQUIRED"));
        if (householdActivityLevel == null)        r = r.and(Validation.invalidOne("householdActivityLevel",   "REQUIRED"));

        if (whyCatsNeedToPlay == null || whyCatsNeedToPlay.isBlank())           r = r.and(Validation.invalidOne("whyCatsNeedToPlay",          "REQUIRED"));
        if (dailyPlayMinutes == null)              r = r.and(Validation.invalidOne("dailyPlayMinutes",         "REQUIRED"));
        else if (dailyPlayMinutes < 0)             r = r.and(Validation.invalidOne("dailyPlayMinutes",         "TOO_SMALL"));
        if (plannedEnrichment == null || plannedEnrichment.isBlank())           r = r.and(Validation.invalidOne("plannedEnrichment",          "REQUIRED"));
        if (reactionToUnwantedBehavior == null || reactionToUnwantedBehavior.isBlank()) r = r.and(Validation.invalidOne("reactionToUnwantedBehavior", "REQUIRED"));
        if (hasScratchingPost == null)             r = r.and(Validation.invalidOne("hasScratchingPost",        "REQUIRED"));
        if (willingToEnrichEnvironment == null)    r = r.and(Validation.invalidOne("willingToEnrichEnvironment", "REQUIRED"));

        if (motivationToAdopt == null || motivationToAdopt.isBlank())           r = r.and(Validation.invalidOne("motivationToAdopt",          "REQUIRED"));
        if (understandsLongTermCommitment == null) r = r.and(Validation.invalidOne("understandsLongTermCommitment", "REQUIRED"));
        if (hasVetBudget == null)                  r = r.and(Validation.invalidOne("hasVetBudget",             "REQUIRED"));
        if (allHouseholdMembersAgree == null)      r = r.and(Validation.invalidOne("allHouseholdMembersAgree", "REQUIRED"));
        if (anyoneHasAllergies == null)            r = r.and(Validation.invalidOne("anyoneHasAllergies",       "REQUIRED"));

        return r;
    }
}
