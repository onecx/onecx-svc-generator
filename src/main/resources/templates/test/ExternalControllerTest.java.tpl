package {{package}}.rs.external.v1.controllers;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tkit.quarkus.jpa.exceptions.ConstraintException;
import org.tkit.quarkus.security.test.GenerateKeycloakClient;
import {{package}}.AbstractTest;
import {{generatedModelPackage}}.{{generatedDto}};
import {{generatedExternalModelPackage}}.{{generatedExternalSearchCriteria}};
import io.quarkus.test.junit.QuarkusTest;
@QuarkusTest
@GenerateKeycloakClient(
        clientName = "{{entityField}}ExternalTestClient",
        scopes = { "{{scopePrefix}}:read", "{{scopePrefix}}:write" }
)
class {{entity}}ControllerTest extends AbstractTest {

    String token;
    String idToken;

    @Inject
    {{entity}}Controller controller;

    @BeforeEach
    void setup() {
        token = keycloakClient.getClientAccessToken("{{entityField}}ExternalTestClient");
        idToken = createToken("org1");
    }
    @Test
    void getById_shouldReturn200() {
        String id = createInternalEntity();

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .when()
                .get("/v1/{{resourcePath}}/{id}", id)
                .then()
                .statusCode(200);
    }
    @Test
    void search_shouldCoverAllBranches() {
        createInternalEntity();

        {{generatedExternalSearchCriteria}} criteria = new {{generatedExternalSearchCriteria}}();

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .contentType(APPLICATION_JSON)
                .body(criteria)
                .when()
                .post("/v1/{{resourcePath}}/search")
                .then()
                .statusCode(200);
    }
    @Test
    void searchWithExplicitPageNumberAndSizeShouldSucceed() {
        createInternalEntity();

        {{generatedExternalSearchCriteria}} criteria = new {{generatedExternalSearchCriteria}}();
        criteria.setPageNumber(2);
        criteria.setPageSize(5);

        given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .contentType(APPLICATION_JSON)
                .body(criteria)
                .when()
                .post("/v1/{{resourcePath}}/search")
                .then()
                .statusCode(200);
    }
    @Test
    void searchWithNullBodyShouldTriggerDaoCatchAndReturnError() {
        createInternalEntity();

        int status = given()
                .auth().oauth2(token)
                .header(APM_HEADER_PARAM, idToken)
                .when()
                .post("/v1/{{resourcePath}}/search")
                .then()
                .extract()
                .statusCode();

        assertTrue(status >= 400);
    }

    @Test
    void shouldMapConstraintException() {
        ConstraintException ex = mock(ConstraintException.class, RETURNS_DEEP_STUBS);
        when(ex.getMessage()).thenReturn("constraint");
        when(ex.getConstraints()).thenReturn("constraint");
        when(ex.getMessageKey().name()).thenReturn("CONSTRAINT_VIOLATIONS");

        assertEquals(400, controller.exception(ex).getStatus());
    }
    @Test
    void shouldMapConstraintViolationException() {
        var ex = new ConstraintViolationException(java.util.Collections.emptySet());

        assertEquals(400, controller.constraint(ex).getStatus());
    }
    @Test
    void shouldMapOptimisticLockException() {
        var ex = new OptimisticLockException("optimistic");
        assertEquals(409, controller.daoException(ex).getStatus());
    }
{{testExternalControllerAdditionalMethods}}
    private String createInternalEntity() {
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
    // DAO exception test moved to a dedicated DAO test in the DAO package because
    // getEntityManager() has protected access and must be referenced from the DAO package.
{{testExternalControllerHelperMethods}}
}
