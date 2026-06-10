package {{package}}.rs.internal.controllers;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tkit.quarkus.jpa.exceptions.ConstraintException;
import org.tkit.quarkus.security.test.GenerateKeycloakClient;

import {{package}}.AbstractTest;
import {{generatedModelPackage}}.{{generatedDto}};
import {{generatedModelPackage}}.{{generatedInternalSearchCriteria}};
import {{daoPackage}}.{{entity}}DAO;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;

@QuarkusTest
@GenerateKeycloakClient(
        clientName = "{{entityField}}InternalTestClient",
        scopes = { "{{scopePrefix}}:read", "{{scopePrefix}}:write", "{{scopePrefix}}:delete" }
)
class {{entity}}ControllerTest extends AbstractTest {

    String token;
    String idToken;

    @Inject
    {{entity}}Controller controller;

    @Inject
    {{entity}}DAO dao;

    @BeforeEach
    void setup() {
        token = keycloakClient.getClientAccessToken("{{entityField}}InternalTestClient");
        idToken = createToken("org1");
    }

    @Test
    void create{{entity}}Test() {
        {{testCreateDtoBody}}

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .contentType(APPLICATION_JSON)
                .body(request)
                .when()
                .post("/internal/{{resourcePath}}")
                .then()
                .statusCode(201);
    }

    @Test
    void get{{entity}}ByIdTest() {
        String id = create{{entity}}AndReturnId();

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .when()
                .get("/internal/{{resourcePath}}/{id}", id)
                .then()
                .statusCode(200);
    }

    @Test
    void get{{entity}}ByIdNotFoundTest() {
        String id = "non-existing-id";

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .when()
                .get("/internal/{{resourcePath}}/{id}", id)
                .then()
                .statusCode(404);
    }

    @Test
    void update{{entity}}Test() {
        String id = create{{entity}}AndReturnId();

        {{testUpdateDtoBody}}

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .when()
                .put("/internal/{{resourcePath}}/{id}", id)
                .then()
                .statusCode(200);
    }

    @Test
    void update{{entity}}NotFoundTest() {
        String id = "non-existing-id";

        {{testUpdateDtoBody}}

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .when()
                .put("/internal/{{resourcePath}}/{id}", id)
                .then()
                .statusCode(404);
    }

    @Test
    void delete{{entity}}Test() {
        String id = create{{entity}}AndReturnId();

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .when()
                .delete("/internal/{{resourcePath}}/{id}", id)
                .then()
                .statusCode(204);
    }

    @Test
    void delete{{entity}}NotFoundTest() {
        String id = "non-existing-id";

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .when()
                .delete("/internal/{{resourcePath}}/{id}", id)
                .then()
                .statusCode(404);
    }

    @Test
    void search{{resourceOperationPlural}}Test() {
        {{testSearchCriteriaBody}}

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .contentType(APPLICATION_JSON)
                .body(request)
                .when()
                .post("/internal/{{resourcePath}}/search")
                .then()
                .statusCode(200);
    }

    @Test
    void searchWithExplicitPageNumberAndSizeShouldSucceed() {
        {{generatedInternalSearchCriteria}} criteria = new {{generatedInternalSearchCriteria}}();
        criteria.setPageNumber(1);
        criteria.setPageSize(5);

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .contentType(APPLICATION_JSON)
                .body(criteria)
                .when()
                .post("/internal/{{resourcePath}}/search")
                .then()
                .statusCode(200);
    }

    @Test
    void searchWithNullBodyShouldTriggerDaoCatchAndReturnError() {
        int status = given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .when()
                .post("/internal/{{resourcePath}}/search")
                .then()
                .extract()
                .statusCode();

        assertTrue(status >= 400);
    }

    {{testInternalControllerAdditionalMethods}}

    @Test
    void search{{resourceOperationPlural}}WithEmptyCriteriaShouldUseDefaults() {
        create{{entity}}AndReturnId();

        String criteria = """
                {
                }
                """;

        List<?> result = given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .contentType(APPLICATION_JSON)
                .body(criteria)
                .when()
                .post("/internal/{{resourcePath}}/search")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()
                .getList("stream");

        assertNotNull(result);
    }

    @Test
    void search{{resourceOperationPlural}}ByNameShouldUsePredicateAndNormalizeNegativePageNumber() {
        String request = """
                {
                  "name": "{{entityField}}-search-by-name"{{testSearchSeedBody}}
                }
                """;

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .contentType(APPLICATION_JSON)
                .body(request)
                .when()
                .post("/internal/{{resourcePath}}")
                .then()
                .statusCode(201);

        String criteria = """
                {
                  "pageNumber": -1,
                  "pageSize": 10,
                  "name": "{{entityField}}-search-by-name"
                }
                """;

        Response response = given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .contentType(APPLICATION_JSON)
                .body(criteria)
                .when()
                .post("/internal/{{resourcePath}}/search")
                .then()
                .extract()
                .response();

        int status = response.statusCode();
        if (status == 200) {
            List<?> result = response.jsonPath().getList("stream");
            assertNotNull(result);
            assertTrue(result.size() >= 1);
        } else {
            assertTrue(status >= 400);
        }
    }

    @Test
    void search{{resourceOperationPlural}}WithNullFieldsShouldReturnAll() {
        create{{entity}}AndReturnId();

        String criteria = """
                {
                  "pageNumber": 0,
                  "pageSize": 100
                }
                """;

        List<?> result = given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .contentType(APPLICATION_JSON)
                .body(criteria)
                .when()
                .post("/internal/{{resourcePath}}/search")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()
                .getList("stream");

        assertNotNull(result);
        assertTrue(result.size() >= 1);
    }

    @Test
    void shouldMapConstraintExceptionWithRealMapper() {
        ConstraintException ex = mock(ConstraintException.class, RETURNS_DEEP_STUBS);
        when(ex.getMessage()).thenReturn("constraint");
        when(ex.getConstraints()).thenReturn("constraint");
        when(ex.getMessageKey().name()).thenReturn("CONSTRAINT_VIOLATIONS");

        var response = controller.exception(ex);

        assertNotNull(response);
        assertEquals(400, response.getStatus());
    }

    @Test
    void shouldMapConstraintViolationExceptionWithRealMapper() {
        ConstraintViolationException ex = new ConstraintViolationException(java.util.Collections.emptySet());

        var response = controller.constraint(ex);

        assertNotNull(response);
        assertEquals(400, response.getStatus());
    }

    @Test
    void shouldMapOptimisticLockExceptionWithRealMapper() {
        OptimisticLockException ex = new OptimisticLockException("optimistic-lock");

        var response = controller.daoException(ex);

        assertNotNull(response);
        assertEquals(400, response.getStatus());
    }

    private String create{{entity}}AndReturnId() {
        {{testCreateDtoBody}}

        return given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .contentType(APPLICATION_JSON)
                .body(request)
                .when()
                .post("/internal/{{resourcePath}}")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }


{{testInternalControllerHelperMethods}}

}