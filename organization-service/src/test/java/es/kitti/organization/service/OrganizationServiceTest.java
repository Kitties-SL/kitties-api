package es.kitti.organization.service;

import es.kitti.mon.either.Either;
import es.kitti.mon.error.DomainError;
import es.kitti.mon.error.ForbiddenError;
import es.kitti.mon.error.NotFoundError;
import es.kitti.organization.client.CatServiceClient;
import es.kitti.organization.client.dto.CountByOrgsRequest;
import es.kitti.organization.client.dto.OrgCatCount;
import es.kitti.organization.dto.CreateOrganizationRequest;
import es.kitti.organization.dto.OrganizationResponse;
import es.kitti.organization.dto.UpdateOrganizationRequest;
import es.kitti.organization.entity.*;
import es.kitti.organization.mapper.OrganizationMapper;
import es.kitti.organization.repository.OrganizationRepository;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock OrganizationRepository organizationRepository;
    @Mock OrganizationMemberService memberService;
    @Mock CatServiceClient catServiceClient;
    @Spy  OrganizationMapper mapper;
    @InjectMocks OrganizationService service;

    private Organization org;
    private OrganizationMember adminMember;

    @BeforeEach
    void setUp() {
        service.internalSecret = "test-secret";

        org = new Organization();
        org.id = 1L;
        org.name = "Protectora Test";
        org.status = OrganizationStatus.Active;
        org.plan = OrganizationPlan.Free;
        org.maxMembers = 1;
        org.createdAt = LocalDateTime.now();
        org.updatedAt = LocalDateTime.now();

        adminMember = new OrganizationMember();
        adminMember.id = 1L;
        adminMember.organizationId = 1L;
        adminMember.userId = 10L;
        adminMember.role = MemberRole.Admin;
        adminMember.status = MemberStatus.Active;
        adminMember.joinedAt = LocalDateTime.now();
    }

    @Test
    void testCreateOrganization() {
        when(organizationRepository.persist(any(Organization.class)))
                .thenReturn(Uni.createFrom().item(org));
        when(memberService.addCreatorAsAdmin(1L, 10L))
                .thenReturn(Uni.createFrom().item(adminMember));

        OrganizationResponse response = service.create(
                new CreateOrganizationRequest("Protectora Test", null, null, null, null, null, null, null, null),
                10L
        ).await().indefinitely();

        assertEquals("Protectora Test", response.name());
    }

    @Test
    void testCreateWithOptionalFields() {
        org.name = "Protectora Completa";
        org.address = "Calle Mayor 1";
        org.city = "Madrid";
        when(organizationRepository.persist(any(Organization.class)))
                .thenReturn(Uni.createFrom().item(org));
        when(memberService.addCreatorAsAdmin(1L, 10L))
                .thenReturn(Uni.createFrom().item(adminMember));

        OrganizationResponse response = service.create(
                new CreateOrganizationRequest("Protectora Completa", "Desc", "Calle Mayor 1", "Madrid", "Madrid", "ES", "600000000", "info@prot.es", null),
                10L
        ).await().indefinitely();

        assertNotNull(response.name());
    }

    @Test
    void testFindByIdSuccess_returnsRight() {
        when(memberService.requireMember(1L, 10L))
                .thenReturn(Uni.createFrom().item(Either.unit()));
        when(organizationRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(org));

        var result = service.findById(1L, 10L).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(1L, result.getOrElse(null).id());
        assertEquals("Protectora Test", result.getOrElse(null).name());
    }

    @Test
    void testFindByIdNotFound_returnsLeft404() {
        when(memberService.requireMember(99L, 10L))
                .thenReturn(Uni.createFrom().item(Either.unit()));
        when(organizationRepository.findById(99L)).thenReturn(Uni.createFrom().nullItem());

        var result = service.findById(99L, 10L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, result.fold(e -> e, __ -> null));
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
    }

    @Test
    void testFindByIdForbidden_returnsLeft403() {
        when(memberService.requireMember(1L, 999L))
                .thenReturn(Uni.createFrom().item(Either.left(new ForbiddenError("FORBIDDEN"))));

        var result = service.findById(1L, 999L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ForbiddenError.class, result.fold(e -> e, __ -> null));
        assertEquals(403, result.fold(DomainError::httpStatus, __ -> 0));
    }

    @Test
    void testFindByCurrentUser_returnsRight() {
        when(memberService.findActiveByUserId(10L))
                .thenReturn(Uni.createFrom().item(Optional.of(adminMember)));
        when(organizationRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(org));

        var result = service.findByCurrentUser(10L).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(1L, result.getOrElse(null).id());
    }

    @Test
    void testFindByCurrentUserNotMember_returnsLeft404() {
        when(memberService.findActiveByUserId(99L))
                .thenReturn(Uni.createFrom().item(Optional.empty()));

        var result = service.findByCurrentUser(99L).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
    }

    @Test
    void testFindByCurrentUser_orgNotFound_returnsLeft404() {
        when(memberService.findActiveByUserId(10L))
                .thenReturn(Uni.createFrom().item(Optional.of(adminMember)));
        when(organizationRepository.findById(1L)).thenReturn(Uni.createFrom().nullItem());

        var result = service.findByCurrentUser(10L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, result.fold(e -> e, __ -> null));
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
    }

    @Test
    void testUpdateForbidden_returnsLeft403() {
        when(memberService.requireAdmin(1L, 20L))
                .thenReturn(Uni.createFrom().item(Either.left(new ForbiddenError("FORBIDDEN"))));

        var result = service.update(1L, 20L,
                new UpdateOrganizationRequest("New Name", null, null, null, null, null, null, null, null))
                .await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(ForbiddenError.class, result.fold(e -> e, __ -> null));
        assertEquals(403, result.fold(DomainError::httpStatus, __ -> 0));
    }

    @Test
    void testUpdateNotFound_returnsLeft404() {
        when(memberService.requireAdmin(1L, 10L)).thenReturn(Uni.createFrom().item(Either.unit()));
        when(organizationRepository.findById(1L)).thenReturn(Uni.createFrom().nullItem());

        var result = service.update(1L, 10L,
                new UpdateOrganizationRequest("Updated", null, null, null, null, null, null, null, null))
                .await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, result.fold(e -> e, __ -> null));
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
    }

    @Test
    void testUpdateByAdmin_returnsRight() {
        when(memberService.requireAdmin(1L, 10L)).thenReturn(Uni.createFrom().item(Either.unit()));
        org.name = "Updated";
        when(organizationRepository.findById(1L)).thenReturn(Uni.createFrom().item(org));
        when(organizationRepository.persist(any(Organization.class))).thenReturn(Uni.createFrom().item(org));

        var result = service.update(1L, 10L,
                new UpdateOrganizationRequest("Updated", null, null, null, null, null, null, null, null))
                .await().indefinitely();

        assertTrue(result.isRight());
        assertEquals("Updated", result.getOrElse(null).name());
    }

    @Test
    void testUpdatePartialFields_onlyUpdatesNonNullFields() {
        org.name = "Original";
        org.description = "Desc";
        when(memberService.requireAdmin(1L, 10L)).thenReturn(Uni.createFrom().item(Either.unit()));
        when(organizationRepository.findById(1L)).thenReturn(Uni.createFrom().item(org));
        when(organizationRepository.persist(any(Organization.class))).thenReturn(Uni.createFrom().item(org));

        service.update(1L, 10L,
                new UpdateOrganizationRequest("New Name", null, null, null, null, null, null, null, null))
                .await().indefinitely();

        assertEquals("New Name", org.name);
        assertEquals("Desc", org.description);
    }

    // --- search (público) ---

    @Test
    void search_noFilters_returnsPageWithCounts() {
        Organization other = activeOrg(2L, "Otra Protectora");
        when(organizationRepository.search(null, null, null, 0, 20))
                .thenReturn(Uni.createFrom().item(List.of(org, other)));
        when(organizationRepository.countSearch(null, null, null))
                .thenReturn(Uni.createFrom().item(2L));
        when(catServiceClient.countByOrgs(any(CountByOrgsRequest.class), eq("test-secret")))
                .thenReturn(Uni.createFrom().item(List.of(
                        new OrgCatCount(1L, 5L),
                        new OrgCatCount(2L, 0L)
                )));

        var result = service.search(null, null, null, 0, 20).await().indefinitely();

        assertEquals(2, result.content().size());
        assertEquals(2L, result.total());
        assertEquals(5L, result.content().get(0).activeCatsCount());
        assertEquals(0L, result.content().get(1).activeCatsCount());
    }

    @Test
    void search_emptyResult_skipsCatServiceCall() {
        when(organizationRepository.search("nope", null, null, 0, 20))
                .thenReturn(Uni.createFrom().item(List.of()));
        when(organizationRepository.countSearch("nope", null, null))
                .thenReturn(Uni.createFrom().item(0L));

        var result = service.search("nope", null, null, 0, 20).await().indefinitely();

        assertTrue(result.content().isEmpty());
        assertEquals(0L, result.total());
        verify(catServiceClient, never()).countByOrgs(any(), any());
    }

    @Test
    void search_catServiceFails_fillsCountsWithZero() {
        when(organizationRepository.search(null, null, null, 0, 20))
                .thenReturn(Uni.createFrom().item(List.of(org)));
        when(organizationRepository.countSearch(null, null, null))
                .thenReturn(Uni.createFrom().item(1L));
        when(catServiceClient.countByOrgs(any(CountByOrgsRequest.class), eq("test-secret")))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("cat-service down")));

        var result = service.search(null, null, null, 0, 20).await().indefinitely();

        assertEquals(1, result.content().size());
        assertEquals(0L, result.content().get(0).activeCatsCount());
    }

    @Test
    void search_sizeExceedsMax_capsAt100() {
        when(organizationRepository.search(null, null, null, 0, 100))
                .thenReturn(Uni.createFrom().item(List.of()));
        when(organizationRepository.countSearch(null, null, null))
                .thenReturn(Uni.createFrom().item(0L));

        service.search(null, null, null, 0, 500).await().indefinitely();

        verify(organizationRepository).search(null, null, null, 0, 100);
    }

    // --- findPublicById ---

    @Test
    void findPublicById_active_returnsRightWithCount() {
        when(organizationRepository.findById(1L)).thenReturn(Uni.createFrom().item(org));
        when(catServiceClient.countByOrgs(any(CountByOrgsRequest.class), eq("test-secret")))
                .thenReturn(Uni.createFrom().item(List.of(new OrgCatCount(1L, 12L))));

        var result = service.findPublicById(1L).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(1L, result.getOrElse(null).id());
        assertEquals(12L, result.getOrElse(null).activeCatsCount());
    }

    @Test
    void findPublicById_notFound_returnsLeft404() {
        when(organizationRepository.findById(999L)).thenReturn(Uni.createFrom().nullItem());

        var result = service.findPublicById(999L).await().indefinitely();

        assertTrue(result.isLeft());
        assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
        verify(catServiceClient, never()).countByOrgs(any(), any());
    }

    @Test
    void findPublicById_notActive_returnsLeft404() {
        org.status = OrganizationStatus.Pending;
        when(organizationRepository.findById(1L)).thenReturn(Uni.createFrom().item(org));

        var result = service.findPublicById(1L).await().indefinitely();

        assertTrue(result.isLeft());
        assertInstanceOf(NotFoundError.class, result.fold(e -> e, __ -> null));
        verify(catServiceClient, never()).countByOrgs(any(), any());
    }

    @Test
    void findPublicById_catServiceFails_returnsRightWithZeroCount() {
        when(organizationRepository.findById(1L)).thenReturn(Uni.createFrom().item(org));
        when(catServiceClient.countByOrgs(any(CountByOrgsRequest.class), eq("test-secret")))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("cat-service down")));

        var result = service.findPublicById(1L).await().indefinitely();

        assertTrue(result.isRight());
        assertEquals(0L, result.getOrElse(null).activeCatsCount());
    }

    private Organization activeOrg(Long id, String name) {
        Organization o = new Organization();
        o.id = id;
        o.name = name;
        o.status = OrganizationStatus.Active;
        o.plan = OrganizationPlan.Free;
        o.maxMembers = 1;
        o.createdAt = LocalDateTime.now();
        o.updatedAt = LocalDateTime.now();
        return o;
    }
}
