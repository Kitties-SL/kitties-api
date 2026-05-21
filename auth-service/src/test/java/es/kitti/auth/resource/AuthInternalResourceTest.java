package es.kitti.auth.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuthInternalResourceTest {

    @Test
    void issuePasswordResetToken_withInternalSecret_returns200() {
        given()
                .header("X-Internal-Token", "test-internal-secret")
                .contentType(ContentType.JSON)
                .body("{\"userId\": 123}")
                .when()
                .post("/auth/internal/password-reset-token")
                .then()
                .statusCode(200)
                .body("token",     notNullValue())
                .body("jti",       notNullValue())
                .body("expiresAt", notNullValue());
    }

    @Test
    void issuePasswordResetToken_withoutInternalSecret_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"userId\": 123}")
                .when()
                .post("/auth/internal/password-reset-token")
                .then()
                .statusCode(401);
    }

    @Test
    void issuePasswordResetToken_withWrongInternalSecret_returns401() {
        given()
                .header("X-Internal-Token", "wrong")
                .contentType(ContentType.JSON)
                .body("{\"userId\": 123}")
                .when()
                .post("/auth/internal/password-reset-token")
                .then()
                .statusCode(401);
    }
}
