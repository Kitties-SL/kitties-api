package es.kitti.adoption.exception;

import io.quarkus.logging.Log;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.LocalDateTime;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof jakarta.ws.rs.NotFoundException) {
            return Response.status(404)
                    .entity(new ErrorResponse(404, exception.getMessage()))
                    .build();
        }
        Log.errorf(exception, "Unexpected exception: %s", exception.getMessage());
        return Response.status(500)
                .entity(new ErrorResponse(500, "An unexpected error occurred"))
                .build();
    }
}
