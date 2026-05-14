package es.kitti.storage.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.util.concurrent.ExecutionException;

@Readiness
@ApplicationScoped
public class MinioHealthCheck implements HealthCheck {

    @Inject
    S3AsyncClient s3;

    @ConfigProperty(name = "bucket.name")
    String bucketName;

    @Override
    public HealthCheckResponse call() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucketName).build()).get();
            return HealthCheckResponse.up("minio");
        } catch (ExecutionException e) {
            String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            return HealthCheckResponse.named("minio").down().withData("error", cause).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HealthCheckResponse.named("minio").down().withData("error", "interrupted").build();
        }
    }
}