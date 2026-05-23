package es.kitti.adoption.intake.mapper;

import es.kitti.adoption.client.dto.CatCreateInternalRequest;
import es.kitti.adoption.intake.dto.IntakeApproveRequest;
import es.kitti.adoption.intake.dto.IntakeRequestCreateRequest;
import es.kitti.adoption.intake.dto.IntakeRequestResponse;
import es.kitti.adoption.intake.entity.IntakeRequest;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class IntakeMapper {

    public IntakeRequest toEntity(IntakeRequestCreateRequest request, Long userId) {
        IntakeRequest entity = new IntakeRequest();
        entity.userId = userId;
        entity.targetOrganizationId = request.targetOrganizationId();
        entity.catName = request.catName();
        entity.catAge = request.catAge();
        entity.region = request.region();
        entity.city = request.city();
        entity.vaccinated = request.vaccinated();
        entity.description = request.description();
        return entity;
    }

    public IntakeRequestResponse toResponse(IntakeRequest entity) {
        return new IntakeRequestResponse(
                entity.id,
                entity.userId,
                entity.targetOrganizationId,
                entity.catName,
                entity.catAge,
                entity.region,
                entity.city,
                entity.vaccinated,
                entity.description,
                entity.status,
                entity.rejectionReason,
                entity.createdAt,
                entity.decidedAt
        );
    }

    public CatCreateInternalRequest toCatCreateInternal(IntakeRequest intake, IntakeApproveRequest approve) {
        return new CatCreateInternalRequest(
                approve.name()        != null ? approve.name()        : intake.catName,
                approve.age()         != null ? approve.age()         : intake.catAge,
                approve.sex(),
                approve.description() != null ? approve.description() : intake.description,
                approve.neutered(),
                approve.city()        != null ? approve.city()        : intake.city,
                approve.region()      != null ? approve.region()      : intake.region,
                approve.country(),
                null,
                null,
                intake.targetOrganizationId
        );
    }
}
