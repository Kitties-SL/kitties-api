package es.kitti.notification.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class NotificationResourceTest {

    @Test
    void list_unauthorized_returns401() {
        given()
                .when()
                .get("/notifications")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "100", roles = "User")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "100")})
    void list_authenticated_returns200() {
        given()
                .when()
                .get("/notifications")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    @TestSecurity(user = "100", roles = "User")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "100")})
    void unreadCount_returns200_withZero() {
        given()
                .when()
                .get("/notifications/unread-count")
                .then()
                .statusCode(200)
                .body("count", equalTo(0));
    }

    @Test
    @TestSecurity(user = "100", roles = "User")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "100")})
    void markRead_unknownId_returns404() {
        given()
                .contentType("application/json")
                .when()
                .patch("/notifications/999/read")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "100", roles = "User")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "100")})
    void markAllRead_returns200() {
        given()
                .contentType("application/json")
                .when()
                .patch("/notifications/read-all")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "100", roles = "Organization")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "100")})
    void list_asOrganization_returns403() {
        given()
                .when()
                .get("/notifications")
                .then()
                .statusCode(403);
    }
}
