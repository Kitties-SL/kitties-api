package es.kitti.organization.resource;

import es.kitti.organization.dto.MembershipResponse;
import es.kitti.organization.dto.OrganizationPublicMinimalResponse;
import es.kitti.organization.repository.OrganizationRepository;
import es.kitti.organization.security.InternalOnly;
import es.kitti.organization.service.OrganizationMemberService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/organizations/internal")
@Produces(MediaType.APPLICATION_JSON)
@InternalOnly
public class OrganizationInternalResource {

    @Inject OrganizationRepository repository;
    @Inject OrganizationMemberService memberService;

    @GET
    @Path("/by-region/{region}")
    @WithSession
    public Uni<List<OrganizationPublicMinimalResponse>> findByRegion(@PathParam("region") String region) {
        return repository.findActiveByRegion(region)
                .onItem().transform(list -> list.stream()
                        .map(o -> new OrganizationPublicMinimalResponse(
                                o.id, o.name, o.city, o.region, o.phone, o.email))
                        .toList());
    }

    @GET
    @Path("/membership/{userId}")
    @WithSession
    public Uni<Response> getMembership(@PathParam("userId") Long userId) {
        return memberService.findActiveByUserId(userId)
                .onItem().transform(opt -> opt
                        .map(m -> Response.ok(new MembershipResponse(m.organizationId, m.role.name())).build())
                        .orElse(Response.status(Response.Status.NOT_FOUND).build()));
    }
}
