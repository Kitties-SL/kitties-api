package es.kitti.organization.exception;

import es.kitti.mon.error.ConstraintViolationMapper;
import es.kitti.mon.error.ErrorResponse;
import es.kitti.mon.error.ForbiddenError;
import io.quarkus.logging.Log;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;


@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable exception) {
        return switch (exception) {
            case ConstraintViolationException cve -> {
                var error = ConstraintViolationMapper.toValidationError(cve.getConstraintViolations());
                yield Response.status(422).entity(ErrorResponse.of(error)).build();
            }
            case ForbiddenException __ ->
                Response.status(403).entity(ErrorResponse.of(new ForbiddenError("FORBIDDEN"))).build();
            default -> {
                Log.errorf(exception, "Unhandled exception: %s", exception.getMessage());
                yield Response.status(500).entity(ErrorResponse.internalError())
                        .build();
            }
        };
    }
}
