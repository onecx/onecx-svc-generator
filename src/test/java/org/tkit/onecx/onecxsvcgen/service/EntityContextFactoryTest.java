package org.tkit.onecx.onecxsvcgen.service;

import org.junit.jupiter.api.Test;
import org.tkit.onecx.onecxsvcgen.model.ApiDef;
import org.tkit.onecx.onecxsvcgen.model.EntityDef;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityContextFactoryTest {

    @Test
    void buildContextUsesExternalPageResultDtoForRootExternalMapper() {
        EntityContextFactory factory = new EntityContextFactory();
        factory.naming = new NamingService();
        factory.models = new ModelParserService();

        Map<String, Object> ctx = factory.buildContext(
                "orders-svc",
                "org.tkit.onecx.orders",
                "ocx-demo",
                new EntityDef("Product", true, new ApiDef(true, null, null, false, null, null), List.of(), List.of())
        );

        assertEquals("ProductPageResultDTOV1", ctx.get("generatedExternalPageResultDto"));
        assertTrue(ctx.get("externalMapperPageResultImports").toString().contains("ProductPageResultDTOV1"));
        assertTrue(ctx.get("mapPageResultMethod").toString().contains("ProductPageResultDTOV1"));
    }
}

