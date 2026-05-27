package es.kitti.notification.resource;

import es.kitti.notification.entity.Notification;
import es.kitti.notification.entity.NotificationType;
import es.kitti.notification.service.NotificationWriteService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class NotificationResourceTest {

    @Inject
    NotificationWriteService writeService;

    private Notification createNotification(Long userId) {
        return writeService.create(
                userId, NotificationType.AdoptionDecision,
                "ADOPTION_APPROVED", "Aprobada", null, "{\"adoptionRequestId\":1}"
        ).await().indefinitely();
    }

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

    // --- Happy path with real data ---

    @Test
    @TestSecurity(user = "500", roles = "User")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "500")})
    void markRead_ownNotification_returns200() {
        var notification = createNotification(500L);

        given()
                .contentType("application/json")
                .when()
                .patch("/notifications/" + notification.id + "/read")
                .then()
                .statusCode(200)
                .body("read", equalTo(true))
                .body("readAt", notNullValue());
    }

    @Test
    @TestSecurity(user = "501", roles = "User")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "501")})
    void markRead_otherUsersNotification_returns403() {
        var notification = createNotification(999L);

        given()
                .contentType("application/json")
                .when()
                .patch("/notifications/" + notification.id + "/read")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "502", roles = "User")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "502")})
    void markRead_twice_isIdempotent() {
        var notification = createNotification(502L);

        var readAt = given()
                .contentType("application/json")
                .when()
                .patch("/notifications/" + notification.id + "/read")
                .then()
                .statusCode(200)
                .body("read", equalTo(true))
                .extract().path("readAt").toString();

        given()
                .contentType("application/json")
                .when()
                .patch("/notifications/" + notification.id + "/read")
                .then()
                .statusCode(200)
                .body("read", equalTo(true))
                .body("readAt", equalTo(readAt));
    }

    @Test
    @TestSecurity(user = "503", roles = "User")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "503")})
    void list_afterCreating_returnsNotification() {
        createNotification(503L);

        given()
                .when()
                .get("/notifications")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].code", equalTo("ADOPTION_APPROVED"))
                .body("[0].read", equalTo(false));
    }

    @Test
    @TestSecurity(user = "504", roles = "User")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "504")})
    void unreadCount_afterCreating_returnsOne() {
        createNotification(504L);

        given()
                .when()
                .get("/notifications/unread-count")
                .then()
                .statusCode(200)
                .body("count", equalTo(1));
    }

    @Test
    @TestSecurity(user = "505", roles = "User")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "505")})
    void markAllRead_marksAllAndCountDropsToZero() {
        createNotification(505L);
        createNotification(505L);

        given()
                .contentType("application/json")
                .when()
                .patch("/notifications/read-all")
                .then()
                .statusCode(200);

        given()
                .when()
                .get("/notifications/unread-count")
                .then()
                .statusCode(200)
                .body("count", equalTo(0));
    }

    @Test
    @TestSecurity(user = "506", roles = "User")
    @JwtSecurity(claims = {@Claim(key = "sub", value = "506")})
    void list_doesNotShowOtherUsersNotifications() {
        createNotification(999L);

        given()
                .when()
                .get("/notifications")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }
}
