package es.kitti.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import es.kitti.mon.either.Validation;
import es.kitti.user.domain.ActivationToken;

public record ActivationRequest(@JsonProperty("token") String token) {

    public Validation<ActivationRequest> validate() {
        return ActivationToken.of(token).map(__ -> this);
    }
}
