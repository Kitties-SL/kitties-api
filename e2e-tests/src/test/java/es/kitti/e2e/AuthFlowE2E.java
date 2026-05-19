package es.kitti.e2e;

import es.kitti.e2e.support.E2EConfig;
import es.kitti.e2e.support.MailHogClient;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AuthFlowE2E {

    private static final long TS = System.currentTimeMillis();

    private static final String EMAIL = "auth_" + TS + "@e2e.test";
    private static final String PASSWORD = "Password1!";
    // Dedicated email for rate-limit test — avoids counter contamination from other tests
    private static final String RATE_LIMIT_EMAIL = "rl_" + TS + "@e2e.test";
    // Dedicated user for password-change test — changing PASSWORD on the main user breaks other tests
    private static final String PWD_EMAIL    = "pwd_" + TS + "@e2e.test";
    private static final String PWD_NEW      = "NewPassword2@";

    @BeforeAll
    static void setup() {
        E2EConfig.waitForStack();
        MailHogClient.deleteAll();

        // Register main user
        given().contentType(ContentType.JSON)
            .body(Map.of("email", EMAIL, "password", PASSWORD,
                "name", "Auth", "surname", "Tester", "role", "User"))
            .post("/api/users").then().statusCode(201);

        String body = MailHogClient.waitForEmail(EMAIL);
        String token = MailHogClient.extractActivationToken(body);
        given().contentType(ContentType.JSON)
            .body(Map.of("token", token))
            .post("/api/users/activate").then().statusCode(200);

        // Register rate-limit user (no activation needed — login will return 401 with wrong pw anyway)
        given().contentType(ContentType.JSON)
            .body(Map.of("email", RATE_LIMIT_EMAIL, "password", PASSWORD,
                "name", "Rate", "surname", "Tester", "role", "User"))
            .post("/api/users").then().statusCode(201);

        // Register + activate password-change user
        given().contentType(ContentType.JSON)
            .body(Map.of("email", PWD_EMAIL, "password", PASSWORD,
                "name", "Pwd", "surname", "Changer", "role", "User"))
            .post("/api/users").then().statusCode(201);
        String pwdBody  = MailHogClient.waitForEmail(PWD_EMAIL);
        String pwdToken = MailHogClient.extractActivationToken(pwdBody);
        given().contentType(ContentType.JSON)
            .body(Map.of("token", pwdToken))
            .post("/api/users/activate").then().statusCode(200);
    }

    @Test
    void login_validCredentials_returnsTokenPair() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", EMAIL, "password", PASSWORD))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .body("refreshToken", notNullValue());
    }

    @Test
    void login_wrongPassword_returns401() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", EMAIL, "password", "WrongPass99!"))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(401);
    }

    @Test
    void login_unknownEmail_returns401() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", "nobody_" + TS + "@e2e.test", "password", PASSWORD))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(401);
    }

    @Test
    void refresh_validToken_rotatesTokenPair() {
        String refreshToken = given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", EMAIL, "password", PASSWORD))
            .post("/api/auth/login").then().statusCode(200)
            .extract().jsonPath().getString("refreshToken");

        Response resp = given()
            .contentType(ContentType.JSON)
            .body(Map.of("refreshToken", refreshToken))
        .when()
            .post("/api/auth/refresh")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .body("refreshToken", notNullValue())
            .extract().response();

        // New refresh token must differ from old one (token rotation)
        String newRefresh = resp.jsonPath().getString("refreshToken");
        assertNotEquals(refreshToken, newRefresh);
    }

    @Test
    void refresh_invalidToken_returns401() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("refreshToken", UUID.randomUUID().toString()))
        .when()
            .post("/api/auth/refresh")
        .then()
            .statusCode(401);
    }

    @Test
    void logout_thenRefreshRejected() {
        String refreshToken = given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", EMAIL, "password", PASSWORD))
            .post("/api/auth/login").then().statusCode(200)
            .extract().jsonPath().getString("refreshToken");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("refreshToken", refreshToken))
        .when()
            .post("/api/auth/logout")
        .then()
            .statusCode(204);

        given()
            .contentType(ContentType.JSON)
            .body(Map.of("refreshToken", refreshToken))
        .when()
            .post("/api/auth/refresh")
        .then()
            .statusCode(401);
    }

    @Test
    void changePassword_revokesAllTokensAndAcceptsNewPassword() {
        // 1. Login con la pass original → tokens válidos
        Response loginOld = given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", PWD_EMAIL, "password", PASSWORD))
            .post("/api/auth/login").then().statusCode(200).extract().response();

        String accessOld  = loginOld.jsonPath().getString("accessToken");
        String refreshOld = loginOld.jsonPath().getString("refreshToken");

        // 2. Cambio de password → 204
        given()
            .header("Authorization", "Bearer " + accessOld)
            .contentType(ContentType.JSON)
            .body(Map.of("currentPassword", PASSWORD, "newPassword", PWD_NEW))
        .when()
            .post("/api/users/me/password")
        .then()
            .statusCode(204);

        // 3. El refresh token anterior queda invalidado (revocación de todos los tokens)
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("refreshToken", refreshOld))
        .when()
            .post("/api/auth/refresh")
        .then()
            .statusCode(401);

        // 4. Login con la pass antigua → 401
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", PWD_EMAIL, "password", PASSWORD))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(401);

        // 5. Login con la pass nueva → 200
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", PWD_EMAIL, "password", PWD_NEW))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body("accessToken",  notNullValue())
            .body("refreshToken", notNullValue());
    }

    @Test
    void changePassword_wrongCurrent_returns400() {
        // Usa el user principal (no cambia su pass — solo lee el hash)
        String accessToken = given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", EMAIL, "password", PASSWORD))
            .post("/api/auth/login").then().statusCode(200)
            .extract().jsonPath().getString("accessToken");

        given()
            .header("Authorization", "Bearer " + accessToken)
            .contentType(ContentType.JSON)
            .body(Map.of("currentPassword", "WrongCurrent!", "newPassword", "anotherValidPass1"))
        .when()
            .post("/api/users/me/password")
        .then()
            .statusCode(400)
            .body("code", org.hamcrest.Matchers.equalTo("INVALID_CURRENT_PASSWORD"));
    }

    @Test
    void changePassword_weakNewPassword_returns422() {
        String accessToken = given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", EMAIL, "password", PASSWORD))
            .post("/api/auth/login").then().statusCode(200)
            .extract().jsonPath().getString("accessToken");

        given()
            .header("Authorization", "Bearer " + accessToken)
            .contentType(ContentType.JSON)
            .body(Map.of("currentPassword", PASSWORD, "newPassword", "short"))
        .when()
            .post("/api/users/me/password")
        .then()
            .statusCode(422)
            .body("violations.find { it.field == 'newPassword' }.code",
                  org.hamcrest.Matchers.equalTo("INVALID_SIZE"));
    }

    @Test
    void rollbackPassword_unknownToken_returns404() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("token", "non-existent-rollback-token"))
        .when()
            .post("/api/users/password/rollback")
        .then()
            .statusCode(404)
            .body("code", org.hamcrest.Matchers.equalTo("ROLLBACK_TOKEN_INVALID"));
    }

    @Test
    void rollbackPassword_isPublic_acceptsRequestWithoutAuth() {
        // Sin Authorization header: el endpoint es público (basta con el token en el body)
        // → no 401, sino 422 por validación del body
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("token", ""))
        .when()
            .post("/api/users/password/rollback")
        .then()
            .statusCode(422)
            .body("violations.find { it.field == 'token' }.code",
                  org.hamcrest.Matchers.equalTo("REQUIRED"));
    }

    @Test
    void changePassword_noToken_returns401() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("currentPassword", PASSWORD, "newPassword", "anotherValidPass1"))
        .when()
            .post("/api/users/me/password")
        .then()
            .statusCode(401);
    }

    @Test
    void rateLimiting_login_returns429() {
        // Fire 11 rapid login attempts with rate-limit-dedicated email
        // Gateway allows 10/min; the 11th must be throttled
        int lastStatus = 0;
        for (int i = 0; i < 11; i++) {
            lastStatus = given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", RATE_LIMIT_EMAIL, "password", "WrongPass99!"))
                .post("/api/auth/login")
                .statusCode();
        }
        org.junit.jupiter.api.Assertions.assertEquals(429, lastStatus,
                "Expected 429 Too Many Requests on the 11th attempt");
    }
}
