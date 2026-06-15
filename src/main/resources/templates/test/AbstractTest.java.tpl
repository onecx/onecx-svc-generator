package {{package}};

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.security.PrivateKey;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.container.ContainerRequestContext;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.tkit.quarkus.rs.context.tenant.RestContextTenantResolverService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.quarkus.test.InjectMock;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.RestAssured;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.util.KeyUtils;

public abstract class AbstractTest {

    protected static final String ADMIN = "alice";
    protected static final String USER = "bob";

    public KeycloakTestClient keycloakClient = new KeycloakTestClient();

    protected static final String APM_HEADER_PARAM;
    protected static final String CLAIMS_ORG_ID;
    private static final PrivateKey PRIVATE_KEY;

    static {
        try {
            Config cfg = ConfigProvider.getConfig();
            APM_HEADER_PARAM = cfg.getValue("%test.tkit.rs.context.token.header-param", String.class);
            CLAIMS_ORG_ID = cfg.getValue("%test.tkit.rs.context.tenant-id.mock.claim-org-id", String.class);
            PRIVATE_KEY = KeyUtils.generateKeyPair(2048).getPrivate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize AbstractTest", e);
        }
    }

    static {
        RestAssured.config = RestAssuredConfig.config().objectMapperConfig(
                ObjectMapperConfig.objectMapperConfig().jackson2ObjectMapperFactory(
                        (cls, charset) -> {
                            ObjectMapper objectMapper = new ObjectMapper();
                            objectMapper.registerModule(new JavaTimeModule());
                            objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
                            return objectMapper;
                        }));
    }

    @InjectMock
    RestContextTenantResolverService tenantResolver;

    @BeforeEach
    void mockTenantResolver() {
        when(tenantResolver.getTenantId(any(JsonWebToken.class), any(ContainerRequestContext.class)))
                .thenReturn("org1");
    }

    protected static String createToken(String organizationId) {
        try {
            String userName = "test-user";
            JsonObjectBuilder claims = Json.createObjectBuilder();
            claims.add(Claims.preferred_username.name(), userName);
            claims.add(Claims.sub.name(), userName);
            if (organizationId != null) {
                claims.add(CLAIMS_ORG_ID, organizationId);
            }

            return Jwt.claims(claims.build()).sign(PRIVATE_KEY);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}