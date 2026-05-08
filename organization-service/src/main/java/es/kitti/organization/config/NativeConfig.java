package es.kitti.organization.config;

import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.FieldViolation;
import es.kitti.mon.error.ValidationError;
import es.kitti.organization.dto.ChangeMemberRoleRequest;
import es.kitti.organization.dto.CreateOrganizationRequest;
import es.kitti.organization.dto.InviteMemberRequest;
import es.kitti.organization.dto.MemberResponse;
import es.kitti.organization.dto.OrganizationPublicMinimalResponse;
import es.kitti.organization.dto.OrganizationResponse;
import es.kitti.organization.dto.UpdateOrganizationRequest;
import es.kitti.organization.entity.MemberRole;
import es.kitti.organization.entity.MemberStatus;
import es.kitti.organization.entity.OrganizationPlan;
import es.kitti.organization.entity.OrganizationStatus;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
        ChangeMemberRoleRequest.class,
        CreateOrganizationRequest.class,
        InviteMemberRequest.class,
        MemberResponse.class,
        OrganizationPublicMinimalResponse.class,
        OrganizationResponse.class,
        UpdateOrganizationRequest.class,
        MemberRole.class,
        MemberStatus.class,
        OrganizationPlan.class,
        OrganizationStatus.class,
        ErrorResponse.class,
        FieldViolation.class,
        ValidationError.class
})
public class NativeConfig {}
