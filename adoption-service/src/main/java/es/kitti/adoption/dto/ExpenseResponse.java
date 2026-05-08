package es.kitti.adoption.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.adoption.entity.ExpenseRecipient;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExpenseResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("adoptionRequestId") Long adoptionRequestId,
        @JsonProperty("concept") String concept,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("recipient") ExpenseRecipient recipient,
        @JsonProperty("paid") Boolean paid,
        @JsonProperty("paidAt") LocalDateTime paidAt,
        @JsonProperty("createdAt") LocalDateTime createdAt,
        @JsonProperty("updatedAt") LocalDateTime updatedAt
) {}
