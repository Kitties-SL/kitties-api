package es.kitti.cat.resource;

import es.kitti.cat.entity.Cat;
import es.kitti.cat.entity.CatSex;
import es.kitti.cat.repository.CatRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.restassured.http.ContentType;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import es.kitti.cat.client.AdoptionClient;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
class CatResourceTest {

    @InjectMock
    @RestClient
    AdoptionClient adoptionClient;

    @Inject
    Vertx vertx;

    @Inject
    CatRepository catRepository;

    private Cat persistInContext(Cat cat) {
        CompletableFuture<Cat> future = new CompletableFuture<>();
        Context duplicated = VertxContext.getOrCreateDuplicatedContext(vertx);
        VertxContextSafetyToggle.setContextSafe(duplicated, true);
        duplicated.runOnContext(__ ->
                Panache.withTransaction(() -> catRepository.persist(cat))
                        .subscribe().with(future::complete, future::completeExceptionally)
        );
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testSearchCatsPublic() {
        given()
                .when()
                .get("/cats")
                .then()
                .statusCode(200);
    }

    @Test
    void testGetCatNotFound() {
        given()
                .when()
                .get("/cats/999999")
                .then()
                .statusCode(404);
    }

    @Test
    void testCreateCatUnauthorized() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "name": "Peluso",
                    "age": 2,
                    "sex": "Male",
                    "neutered": true,
                    "city": "La Orotava",
                    "region": "Tenerife",
                    "country": "España"
                }
                """)
                .when()
                .post("/cats")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "1", roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kitti.es")
    })
    void testCreateCatAsRegularUserForbidden() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "name": "Peluso",
                    "age": 2,
                    "sex": "Male",
                    "neutered": true,
                    "city": "La Orotava",
                    "region": "Tenerife",
                    "country": "España"
                }
                """)
                .when()
                .post("/cats")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "1", roles = "Organization")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kitti.es"),
            @Claim(key = "organizationId", value = "1")
    })
    void testCreateCatAsOrganization() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "name": "Peluso",
                    "age": 2,
                    "sex": "Male",
                    "neutered": true,
                    "city": "La Orotava",
                    "region": "Tenerife",
                    "country": "España"
                }
                """)
                .when()
                .post("/cats")
                .then()
                .statusCode(201)
                .body("name", equalTo("Peluso"))
                .body("status", equalTo("Available"));
    }

    @Test
    @TestSecurity(user = "1", roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kitti.es")
    })
    void testDeleteCatAsRegularUserForbidden() {
        given()
                .when()
                .delete("/cats/999999")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "1", roles = "Organization")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kitti.es"),
            @Claim(key = "organizationId", value = "1")
    })
    void testDeleteCatAsOrganizationNotFound() {
        given()
                .when()
                .delete("/cats/999999")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "1", roles = "Organization")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kitti.es"),
            @Claim(key = "organizationId", value = "1")
    })
    void testDeleteCat_noActiveAdoptions_returns204() {
        Long catId = given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "name": "Misifu",
                    "age": 1,
                    "sex": "Female",
                    "neutered": false,
                    "city": "Santa Cruz",
                    "region": "Tenerife",
                    "country": "España"
                }
                """)
                .when()
                .post("/cats")
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");

        when(adoptionClient.hasActiveRequestsForCat(eq(catId), any()))
                .thenReturn(Uni.createFrom().item(false));

        given()
                .when()
                .delete("/cats/" + catId)
                .then()
                .statusCode(204);

        given()
                .when()
                .get("/cats/" + catId)
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "1", roles = "Organization")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kitti.es"),
            @Claim(key = "organizationId", value = "1")
    })
    void testDeleteCat_hasActiveAdoptions_returns409() {
        Long catId = given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "name": "Ronron",
                    "age": 3,
                    "sex": "Male",
                    "neutered": true,
                    "city": "La Laguna",
                    "region": "Tenerife",
                    "country": "España"
                }
                """)
                .when()
                .post("/cats")
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");

        when(adoptionClient.hasActiveRequestsForCat(eq(catId), any()))
                .thenReturn(Uni.createFrom().item(true));

        given()
                .when()
                .delete("/cats/" + catId)
                .then()
                .statusCode(409);
    }

    @Test
    @TestSecurity(user = "1", roles = "Organization")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "1"),
            @Claim(key = "email", value = "test@kitti.es"),
            @Claim(key = "organizationId", value = "1")
    })
    void testFindMine_excludesDeletedCats() {
        Long catId = given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "name": "Garfield",
                    "age": 5,
                    "sex": "Male",
                    "neutered": true,
                    "city": "Adeje",
                    "region": "Tenerife",
                    "country": "España"
                }
                """)
                .when()
                .post("/cats")
                .then()
                .statusCode(201)
                .extract().jsonPath().getLong("id");

        when(adoptionClient.hasActiveRequestsForCat(eq(catId), any()))
                .thenReturn(Uni.createFrom().item(false));

        given().when().delete("/cats/" + catId).then().statusCode(204);

        given()
                .when()
                .get("/cats/mine")
                .then()
                .statusCode(200)
                .body("id", not(hasItem(catId.intValue())));
    }

    @Test
    @TestSecurity(user = "2", roles = "Organization")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "2"),
            @Claim(key = "email", value = "org2@kitti.es"),
            @Claim(key = "organizationId", value = "2")
    })
    void testUpdateCat_differentOrganization_returns403() {
        Cat cat = new Cat();
        cat.name = "Ajeno";
        cat.sex = CatSex.Male;
        cat.neutered = false;
        cat.city = "TestCity";
        cat.country = "España";
        cat.organizationId = 1L;
        Cat saved = persistInContext(cat);

        given()
                .contentType(ContentType.JSON)
                .body("""
                {
                    "name": "Renombrado",
                    "age": 3,
                    "sex": "Male",
                    "neutered": false,
                    "city": "TestCity",
                    "region": "Tenerife",
                    "country": "España"
                }
                """)
                .when()
                .put("/cats/" + saved.id)
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "2", roles = "Organization")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = "2"),
            @Claim(key = "email", value = "org2@kitti.es"),
            @Claim(key = "organizationId", value = "2")
    })
    void testDeleteCat_differentOrganization_returns403() {
        Cat cat = new Cat();
        cat.name = "AjenoDelete";
        cat.sex = CatSex.Female;
        cat.neutered = true;
        cat.city = "TestCity";
        cat.country = "España";
        cat.organizationId = 1L;
        Cat saved = persistInContext(cat);

        when(adoptionClient.hasActiveRequestsForCat(eq(saved.id), any()))
                .thenReturn(Uni.createFrom().item(false));

        given()
                .when()
                .delete("/cats/" + saved.id)
                .then()
                .statusCode(403);
    }
}
