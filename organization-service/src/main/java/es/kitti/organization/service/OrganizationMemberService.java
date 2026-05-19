package es.kitti.organization.service;

import es.kitti.mon.either.Either;
import es.kitti.mon.either.Unit;
import es.kitti.mon.error.ConflictError;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.ForbiddenError;
import es.kitti.mon.error.NotFoundError;
import es.kitti.organization.dto.ChangeMemberRoleRequest;
import es.kitti.organization.dto.InviteMemberRequest;
import es.kitti.organization.dto.MemberResponse;
import es.kitti.organization.entity.MemberRole;
import es.kitti.organization.entity.MemberStatus;
import es.kitti.organization.entity.OrganizationMember;
import es.kitti.organization.mapper.OrganizationMapper;
import es.kitti.organization.repository.OrganizationMemberRepository;
import es.kitti.organization.repository.OrganizationRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class OrganizationMemberService {

    @Inject OrganizationMemberRepository memberRepository;
    @Inject OrganizationRepository organizationRepository;
    @Inject OrganizationMapper mapper;

    public Uni<OrganizationMember> addCreatorAsAdmin(Long organizationId, Long userId) {
        OrganizationMember member = new OrganizationMember();
        member.organizationId = organizationId;
        member.userId = userId;
        member.role = MemberRole.Admin;
        return memberRepository.persist(member);
    }

    @WithSession
    public Uni<Either<DomainError, List<MemberResponse>>> listMembers(Long organizationId, Long callerId) {
        return requireAdmin(organizationId, callerId)
                .onItem().transformToUni(either -> either.fold(
                        err -> Uni.createFrom().item(Either.left(err)),
                        __ -> memberRepository.findActiveByOrganizationId(organizationId)
                                .onItem().transform(list ->
                                        Either.<DomainError, List<MemberResponse>>right(
                                                list.stream().map(mapper::toResponse).toList()))
                ));
    }

    @WithTransaction
    public Uni<Either<DomainError, MemberResponse>> inviteMember(Long organizationId, Long callerId, InviteMemberRequest request) {
        return requireAdmin(organizationId, callerId)
                .onItem().transformToUni(either -> either.fold(
                        err -> Uni.createFrom().item(Either.left(err)),
                        __ -> organizationRepository.findById(organizationId)
                                .onItem().transformToUni(org -> {
                                    if (org == null)
                                        return Uni.createFrom().item(Either.left(new NotFoundError("ORGANIZATION_NOT_FOUND")));
                                    return memberRepository.countActiveByOrganizationId(organizationId)
                                            .onItem().transformToUni(count -> {
                                                if (org.maxMembers != -1 && count >= org.maxMembers)
                                                    return Uni.createFrom().item(Either.<DomainError, MemberResponse>left(
                                                            new ConflictError("MEMBER_LIMIT_EXCEEDED")));
                                                OrganizationMember member = new OrganizationMember();
                                                member.organizationId = organizationId;
                                                member.userId = request.userId();
                                                member.role = request.role();
                                                return memberRepository.persist(member)
                                                        .onItem().transform(saved ->
                                                                Either.<DomainError, MemberResponse>right(mapper.toResponse(saved)));
                                            });
                                })
                ));
    }

    @WithTransaction
    public Uni<Either<DomainError, MemberResponse>> changeMemberRole(Long organizationId, Long targetUserId, Long callerId, ChangeMemberRoleRequest request) {
        return requireAdmin(organizationId, callerId)
                .onItem().transformToUni(either -> either.fold(
                        err -> Uni.createFrom().item(Either.left(err)),
                        __ -> memberRepository.findActiveByOrganizationIdAndUserId(organizationId, targetUserId)
                                .onItem().transformToUni(opt -> {
                                    if (opt.isEmpty())
                                        return Uni.createFrom().item(Either.<DomainError, MemberResponse>left(
                                                new NotFoundError("MEMBER_NOT_FOUND")));
                                    OrganizationMember member = opt.get();
                                    member.role = request.role();
                                    return memberRepository.persist(member)
                                            .onItem().transform(saved ->
                                                    Either.<DomainError, MemberResponse>right(mapper.toResponse(saved)));
                                })
                ));
    }

    @WithTransaction
    public Uni<Either<DomainError, Unit>> removeMember(Long organizationId, Long targetUserId, Long callerId) {
        return requireAdmin(organizationId, callerId)
                .onItem().transformToUni(either -> either.fold(
                        err -> Uni.createFrom().item(Either.left(err)),
                        __ -> memberRepository.findActiveByOrganizationIdAndUserId(organizationId, targetUserId)
                                .onItem().transformToUni(opt -> {
                                    if (opt.isEmpty())
                                        return Uni.createFrom().item(Either.left(
                                                new NotFoundError("MEMBER_NOT_FOUND")));
                                    OrganizationMember member = opt.get();
                                    member.status = MemberStatus.Removed;
                                    return memberRepository.persist(member)
                                            .onItem().transform(m -> Either.unit());
                                })
                ));
    }

    public Uni<Optional<OrganizationMember>> findActiveByUserId(Long userId) {
        return memberRepository.findActiveByUserId(userId);
    }

    public Uni<Either<DomainError, Unit>> requireAdmin(Long organizationId, Long userId) {
        return memberRepository.isAdmin(organizationId, userId)
                .onItem().transform(isAdmin -> isAdmin
                        ? Either.unit()
                        : Either.<DomainError, Unit>left(new ForbiddenError("FORBIDDEN")));
    }

    public Uni<Either<DomainError, Unit>> requireMember(Long organizationId, Long userId) {
        return memberRepository.isMember(organizationId, userId)
                .onItem().transform(isMember -> isMember
                        ? Either.unit()
                        : Either.<DomainError, Unit>left(new ForbiddenError("FORBIDDEN")));
    }
}
