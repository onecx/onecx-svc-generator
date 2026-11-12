package org.tkit.onecx.onecxsvcgen.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NamingServiceTest {

    private final NamingService naming = new NamingService();

    @Test
    void derivesSlugsFromUnprefixedLegacyAndCustomPrefixedIdentities() {
        assertEquals("orders", naming.artifactSlug("orders-svc"));
        assertEquals("orders", naming.artifactSlug("onecx-orders-svc"));
        assertEquals("acme-orders", naming.artifactSlug("acme-orders-svc"));
    }

    @Test
    void preservesExistingPermissionScopeNamespaceConventions() {
        assertEquals("ocx-orders", naming.scopePrefixFromArtifactId("orders-svc"));
        assertEquals("ocx-orders", naming.scopePrefixFromArtifactId("onecx-orders-svc"));
        assertEquals("ocx-acme-orders", naming.scopePrefixFromArtifactId("acme-orders-svc"));
    }
}
