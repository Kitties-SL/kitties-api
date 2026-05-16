package es.kitti.organization.service;

import es.kitti.organization.dto.RegisterOrganizationRequest;
import es.kitti.organization.entity.Organization;
import es.kitti.organization.entity.OrganizationStatus;
import es.kitti.organization.mapper.OrganizationMapper;
import es.kitti.organization.repository.OrganizationRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OrganizationWriteService {

    @Inject OrganizationRepository organizationRepository;
    @Inject OrganizationMemberService memberService;
    @Inject OrganizationMapper mapper;

    @WithTransaction
    public Uni<Organization> createOrg(RegisterOrganizationRequest request) {
        return organizationRepository.persist(mapper.toEntityFromRegister(request));
    }

    @WithTransaction
    public Uni<Void> addAdminMember(Long orgId, Long userId) {
        return memberService.addCreatorAsAdmin(orgId, userId).replaceWithVoid();
    }

    @WithTransaction
    public Uni<Organization> activateOrg(Long orgId) {
        return organizationRepository.findById(orgId)
                .onItem().transformToUni(org -> {
                    org.status = OrganizationStatus.Active;
                    return organizationRepository.persist(org);
                });
    }
}
