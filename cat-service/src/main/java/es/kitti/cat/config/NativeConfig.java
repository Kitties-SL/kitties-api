package es.kitti.cat.config;

import es.kitti.cat.client.dto.StorageResponse;
import es.kitti.cat.dto.CatCreateRequest;
import es.kitti.cat.dto.CatImageResponse;
import es.kitti.cat.dto.CatInventoryStatsResponse;
import es.kitti.cat.dto.CatResponse;
import es.kitti.cat.dto.CatSummaryResponse;
import es.kitti.cat.dto.CatUpdateRequest;
import es.kitti.cat.dto.PageResponse;
import es.kitti.cat.entity.CatSex;
import es.kitti.cat.entity.CatStatus;
import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.FieldViolation;
import es.kitti.mon.error.ValidationError;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
        CatCreateRequest.class,
        CatImageResponse.class,
        CatInventoryStatsResponse.class,
        CatResponse.class,
        CatSummaryResponse.class,
        CatUpdateRequest.class,
        PageResponse.class,
        StorageResponse.class,
        CatSex.class,
        CatStatus.class,
        ErrorResponse.class,
        FieldViolation.class,
        ValidationError.class
})
public class NativeConfig {}
