package es.kitti.cat.config;

import es.kitti.cat.client.dto.StorageResponse;
import es.kitti.cat.dto.*;
import es.kitti.cat.entity.CatSex;
import es.kitti.cat.entity.CatStatus;
import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.FieldViolation;
import es.kitti.mon.error.ValidationError;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
        CatCreateRequest.class,
        CatCreateInternalRequest.class,
        CatImageResponse.class,
        CatInventoryStatsResponse.class,
        CatResponse.class,
        CatSummaryResponse.class,
        CatUpdateRequest.class,
        CountByOrgsRequest.class,
        OrgCatCountResponse.class,
        PageResponse.class,
        StorageResponse.class,
        CatSex.class,
        CatStatus.class,
        ErrorResponse.class,
        FieldViolation.class,
        ValidationError.class
})
public class NativeConfig {}
