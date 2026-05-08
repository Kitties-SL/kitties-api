package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import es.kitti.adoption.entity.ActivityLevel;
import es.kitti.adoption.entity.HousingType;

public record AdoptionRequestFormCreateRequest(
        @JsonProperty("hasPreviousCatExperience") @NotNull Boolean hasPreviousCatExperience,
        @JsonProperty("previousPetsHistory") String previousPetsHistory,
        @JsonProperty("adultsInHousehold") @NotNull @Min(1) Integer adultsInHousehold,
        @JsonProperty("hasChildren") @NotNull Boolean hasChildren,
        @JsonProperty("childrenAges") String childrenAges,
        @JsonProperty("hasOtherPets") @NotNull Boolean hasOtherPets,
        @JsonProperty("otherPetsDescription") String otherPetsDescription,
        @JsonProperty("hoursAlonePerDay") @NotNull @Min(0) @Max(24) Integer hoursAlonePerDay,
        @JsonProperty("stableHousing") @NotNull Boolean stableHousing,
        @JsonProperty("housingInstabilityReason") String housingInstabilityReason,

        @JsonProperty("housingType") @NotNull HousingType housingType,
        @JsonProperty("housingSize") @NotNull @Min(1) Integer housingSize,
        @JsonProperty("hasOutdoorAccess") @NotNull Boolean hasOutdoorAccess,
        @JsonProperty("isRental") @NotNull Boolean isRental,
        @JsonProperty("rentalPetsAllowed") Boolean rentalPetsAllowed,
        @JsonProperty("hasWindowsWithView") @NotNull Boolean hasWindowsWithView,
        @JsonProperty("hasVerticalSpace") @NotNull Boolean hasVerticalSpace,
        @JsonProperty("hasHidingSpots") @NotNull Boolean hasHidingSpots,
        @JsonProperty("householdActivityLevel") @NotNull ActivityLevel householdActivityLevel,

        @JsonProperty("whyCatsNeedToPlay") @NotBlank String whyCatsNeedToPlay,
        @JsonProperty("dailyPlayMinutes") @NotNull @Min(0) Integer dailyPlayMinutes,
        @JsonProperty("plannedEnrichment") @NotBlank String plannedEnrichment,
        @JsonProperty("reactionToUnwantedBehavior") @NotBlank String reactionToUnwantedBehavior,
        @JsonProperty("hasScratchingPost") @NotNull Boolean hasScratchingPost,
        @JsonProperty("willingToEnrichEnvironment") @NotNull Boolean willingToEnrichEnvironment,

        @JsonProperty("motivationToAdopt") @NotBlank String motivationToAdopt,
        @JsonProperty("understandsLongTermCommitment") @NotNull Boolean understandsLongTermCommitment,
        @JsonProperty("hasVetBudget") @NotNull Boolean hasVetBudget,
        @JsonProperty("allHouseholdMembersAgree") @NotNull Boolean allHouseholdMembersAgree,
        @JsonProperty("anyoneHasAllergies") @NotNull Boolean anyoneHasAllergies,
        @JsonProperty("allergiesDetail") String allergiesDetail
) {}
