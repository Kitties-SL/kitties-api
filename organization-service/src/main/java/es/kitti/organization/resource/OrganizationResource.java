package es.kitti.organization.resource;

import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.ValidationError;
import es.kitti.organization.dto.*;
import es.kitti.organization.service.OrganizationMemberService;
import es.kitti.organization.service.OrganizationService;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/organizations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class OrganizationResource {

    @Inject OrganizationService organizationService;
    @Inject OrganizationMemberService memberService;
    @Inject JsonWebToken jwt;

    @POST
    @Path("/register")
    @PermitAll
    public Uni<Response> register(RegisterOrganizationRequest request) {
        return request.validate().match(
                this::validationFailed,
                valid -> organizationService.register(valid)
                        .onItem().transform(either -> either.fold(
                                err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                org -> Response.status(Response.Status.CREATED).entity(org).build()
                        ))
        );
    }

    @POST
    @RolesAllowed({"Organization", "Admin"})
    public Uni<Response> create(CreateOrganizationRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        return request.validate().match(
                this::validationFailed,
                valid -> organizationService.create(valid, userId)
                        .onItem().transform(r -> Response.status(Response.Status.CREATED).entity(r).build())
        );
    }

    @GET
    @Path("/mine")
    public Uni<Response> mine() {
        Long userId = Long.parseLong(jwt.getSubject());
        return organizationService.findByCurrentUser(userId)
                .onItem().transform(either -> either.fold(
                        err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                        org -> Response.ok(org).build()
                ));
    }

    @GET
    @Path("/{id}")
    public Uni<Response> findById(@PathParam("id") Long id) {
        Long userId = Long.parseLong(jwt.getSubject());
        return organizationService.findById(id, userId)
                .onItem().transform(either -> either.fold(
                        err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                        org -> Response.ok(org).build()
                ));
    }

    @PUT
    @Path("/{id}")
    public Uni<Response> update(@PathParam("id") Long id, UpdateOrganizationRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        return request.validate().match(
                this::validationFailed,
                valid -> organizationService.update(id, userId, valid)
                        .onItem().transform(either -> either.fold(
                                err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                org -> Response.ok(org).build()
                        ))
        );
    }

    @GET
    @Path("/{id}/members")
    public Uni<Response> listMembers(@PathParam("id") Long id) {
        Long userId = Long.parseLong(jwt.getSubject());
        return memberService.listMembers(id, userId)
                .onItem().transform(either -> either.fold(
                        err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                        list -> Response.ok(list).build()
                ));
    }

    @POST
    @Path("/{id}/members")
    public Uni<Response> inviteMember(@PathParam("id") Long id, InviteMemberRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        return request.validate().match(
                this::validationFailed,
                valid -> memberService.inviteMember(id, userId, valid)
                        .onItem().transform(either -> either.fold(
                                err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                r -> Response.status(Response.Status.CREATED).entity(r).build()
                        ))
        );
    }

    @PATCH
    @Path("/{id}/members/{targetUserId}/role")
    public Uni<Response> changeMemberRole(@PathParam("id") Long id,
                                          @PathParam("targetUserId") Long targetUserId,
                                          ChangeMemberRoleRequest request) {
        Long userId = Long.parseLong(jwt.getSubject());
        return request.validate().match(
                this::validationFailed,
                valid -> memberService.changeMemberRole(id, targetUserId, userId, valid)
                        .onItem().transform(either -> either.fold(
                                err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                                r -> Response.ok(r).build()
                        ))
        );
    }

    @DELETE
    @Path("/{id}/members/{targetUserId}")
    public Uni<Response> removeMember(@PathParam("id") Long id, @PathParam("targetUserId") Long targetUserId) {
        Long userId = Long.parseLong(jwt.getSubject());
        return memberService.removeMember(id, targetUserId, userId)
                .onItem().transform(either -> either.fold(
                        err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                        __ -> Response.noContent().build()
                ));
    }

    private Uni<Response> validationFailed(ValidationError err) {
        return Uni.createFrom().item(
                Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build()
        );
    }

}
