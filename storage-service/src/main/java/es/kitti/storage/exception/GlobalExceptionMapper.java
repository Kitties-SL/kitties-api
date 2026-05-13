package es.kitti.storage.exception;

import es.kitti.mon.error.ErrorResponse;
import io.quarkus.logging.Log;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;


@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable exception) {
        return switch (exception) {
            default -> {
                Log.errorf(exception, "Unhandled exception: %s", exception.getMessage());
                yield Response.status(500).entity(ErrorResponse.internalError())
                        .build();
            }
        };
    }
}
