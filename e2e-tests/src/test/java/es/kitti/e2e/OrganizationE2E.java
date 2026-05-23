package es.kitti.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.kitti.e2e.support.E2EConfig;
import es.kitti.e2e.support.MailHogClient;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.Base64;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrganizationE2E {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long TS = System.currentTimeMillis();

    private static final String ADMIN_EMAIL = "org_admin_" + TS + "@e2e.test";
    private static final String USER_EMAIL  = "org_user_"  + TS + "@e2e.test";
    private static final String PASSWORD    = "Password1!";

    private static String adminToken;
    private static Long   adminUserId;
    private static String userToken;
    private static Long   userUserId;
    private static Long   orgId;

    @BeforeAll
    static void setup() {
        E2EConfig.waitForStack();
        MailHogClient.deleteAll();
    }

    // ── Registration ─────────────────────────────────────────────────────────

    @Test @Order(1)
    void registerAdmin() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "email", ADMIN_EMAIL, "password", PASSWORD,
                "name", "Org", "surname", "Admin", "role", "Organization"
            ))
        .when()
            .post("/api/users")
        .then()
            .statusCode(201);
    }

    @Test @Order(2)
    void registerUser() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "email", USER_EMAIL, "password", PASSWORD,
                "name", "Org", "surname", "User", "role", "Organization"
            ))
        .when()
            .post("/api/users")
        .then()
            .statusCode(201);
    }

    @Test @Order(3)
    void activateAdmin() {
        String token = MailHogClient.extractActivationToken(MailHogClient.waitForEmail(ADMIN_EMAIL));
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("token", token))
        .when()
            .post("/api/users/activate")
        .then()
            .statusCode(200);
    }

    @Test @Order(4)
    void activateUser() {
        String token = MailHogClient.extractActivationToken(MailHogClient.waitForEmail(USER_EMAIL));
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("token", token))
        .when()
            .post("/api/users/activate")
        .then()
            .statusCode(200);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test @Order(5)
    void loginAdmin() throws Exception {
        Response resp = given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", ADMIN_EMAIL, "password", PASSWORD))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .extract().response();

        adminToken  = resp.jsonPath().getString("accessToken");
        adminUserId = extractSubFromJwt(adminToken);
    }

    @Test @Order(6)
    void loginUser() throws Exception {
        Response resp = given()
            .contentType(ContentType.JSON)
            .body(Map.of("email", USER_EMAIL, "password", PASSWORD))
        .when()
            .post("/api/auth/login")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .extract().response();

        userToken  = resp.jsonPath().getString("accessToken");
        userUserId = extractSubFromJwt(userToken);
    }

    // ── Organization CRUD ─────────────────────────────────────────────────────

    @Test @Order(7)
    void createOrganization() {
        orgId = given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(Map.of(
                "name", "Protectora Test " + TS,
                "city", "Madrid",
                "country", "España"
            ))
        .when()
            .post("/api/organizations")
        .then()
            .statusCode(201)
            .body("name", containsString("Protectora Test"))
            .body("plan", equalTo("Free"))
            .body("status", equalTo("Active"))
            .extract().jsonPath().getLong("id");

        Assertions.assertNotNull(orgId);
    }

    @Test @Order(8)
    void getMine() {
        given()
            .header("Authorization", "Bearer " + adminToken)
        .when()
            .get("/api/organizations/mine")
        .then()
            .statusCode(200)
            .body("id", equalTo(orgId.intValue()));
    }

    @Test @Order(9)
    void getById() {
        given()
            .header("Authorization", "Bearer " + adminToken)
        .when()
            .get("/api/organizations/" + orgId)
        .then()
            .statusCode(200)
            .body("id", equalTo(orgId.intValue()))
            .body("city", equalTo("Madrid"));
    }

    @Test @Order(10)
    void updateOrganization() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(Map.of("description", "Updated description"))
        .when()
            .put("/api/organizations/" + orgId)
        .then()
            .statusCode(200)
            .body("description", equalTo("Updated description"));
    }

    // ── Member management ─────────────────────────────────────────────────────

    @Test @Order(11)
    void listMembers_adminSeesHimself() {
        given()
            .header("Authorization", "Bearer " + adminToken)
        .when()
            .get("/api/organizations/" + orgId + "/members")
        .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].userId", equalTo(adminUserId.intValue()))
            .body("[0].role", equalTo("Admin"));
    }

    @Test @Order(12)
    void inviteMember_freePlanLimitExceeded() {
        // Free plan allows 1 member; creator already occupies the slot → 409
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(Map.of("userId", userUserId, "role", "Staff"))
        .when()
            .post("/api/organizations/" + orgId + "/members")
        .then()
            .statusCode(409);
    }

    // ── Security ──────────────────────────────────────────────────────────────

    @Test @Order(13)
    void getById_unauthenticated() {
        given()
        .when()
            .get("/api/organizations/" + orgId)
        .then()
            .statusCode(401);
    }

    @Test @Order(14)
    void update_nonMemberForbidden() {
        // orgUser is not a member of this organization → 403
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + userToken)
            .body(Map.of("description", "Hacked"))
        .when()
            .put("/api/organizations/" + orgId)
        .then()
            .statusCode(403);
    }

    @Test @Order(15)
    void getMine_noOrganization() {
        // orgUser has no organization → 404
        given()
            .header("Authorization", "Bearer " + userToken)
        .when()
            .get("/api/organizations/mine")
        .then()
            .statusCode(404);
    }

    // ── Public endpoints (sin autenticación) ──────────────────────────────────

    @Test @Order(16)
    void searchPublic_noAuth_returnsPageWithOrg() {
        given()
        .when()
            .get("/api/organizations")
        .then()
            .statusCode(200)
            .body("content", not(empty()))
            .body("total", greaterThanOrEqualTo(1))
            .body("totalPages", greaterThanOrEqualTo(1))
            .body("content.id", hasItem(orgId.intValue()))
            .body("content.find { it.id == " + orgId + " }.activeCatsCount", notNullValue());
    }

    @Test @Order(17)
    void searchPublic_nameFilter_noMatch_returnsEmpty() {
        given()
            .queryParam("name", "ESTA_ORG_NO_EXISTE_" + TS)
        .when()
            .get("/api/organizations")
        .then()
            .statusCode(200)
            .body("content", empty())
            .body("total", equalTo(0));
    }

    @Test @Order(18)
    void findPublicById_noAuth_returnsPublicSubset() {
        given()
        .when()
            .get("/api/organizations/" + orgId + "/public")
        .then()
            .statusCode(200)
            .body("id", equalTo(orgId.intValue()))
            .body("name", containsString("Protectora Test"))
            .body("city", equalTo("Madrid"))
            .body("plan", equalTo("Free"))
            .body("activeCatsCount", greaterThanOrEqualTo(0))
            // campos sensibles no deben venir en la ficha pública
            .body("$", not(hasKey("email")))
            .body("$", not(hasKey("phone")))
            .body("$", not(hasKey("address")))
            .body("$", not(hasKey("status")));
    }

    @Test @Order(19)
    void findPublicById_unknownOrg_returns404() {
        given()
        .when()
            .get("/api/organizations/9999999/public")
        .then()
            .statusCode(404);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Long extractSubFromJwt(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        JsonNode node = MAPPER.readTree(payload);
        return node.get("sub").asLong();
    }
}
