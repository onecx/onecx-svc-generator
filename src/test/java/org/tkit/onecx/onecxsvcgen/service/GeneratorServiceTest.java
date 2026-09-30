package org.tkit.onecx.onecxsvcgen.service;

import org.junit.jupiter.api.Test;
import org.tkit.onecx.onecxsvcgen.model.CreateSvcRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorServiceTest {

    @Test
    void generateUsesLatestVersionsInTemplateContext() throws Exception {
        GeneratorService service = new GeneratorService();
        CapturingTemplateService templates = new CapturingTemplateService();
        StubLatestVersionResolver latest = new StubLatestVersionResolver(Map.of(
                "onecx/onecx-quarkus3-parent", "3.2.1",
                "onecx/docker-quarkus-jvm", "1.5.0",
                "onecx/docker-quarkus-native", "1.6.0",
                "onecx/helm-quarkus-app", "0.50.0"
        ));

        service.templates = templates;
        service.naming = new NamingService();
        service.buildService = new NoopBuildService();
        service.liquibase = new LiquibaseChangelogService();
        service.latestVersionResolver = latest;

        Path outputDir = Files.createTempDirectory("svc-generator-test-");
        Path root = service.generate(new CreateSvcRequest(
                "onecx-demo-svc",
                "org.tkit.onecx",
                "onecx-demo-svc",
                "org.tkit.onecx.demo",
                outputDir,
                false
        ),null);

        Map<String, Object> pomCtx = templates.contextByTemplate.get("templates/svc-project/pom.xml.tpl");
        assertEquals("3.2.1", pomCtx.get("parentVersion"));
        assertEquals("quarkus-junit", pomCtx.get("junitArtifact"));
        assertEquals("quarkus-junit-mockito", pomCtx.get("junitMockitoArtifact"));

        Map<String, Object> dockerJvmCtx = templates.contextByTemplate.get("templates/svc-project/Dockerfile.jvm.tpl");
        assertEquals("1.5.0", dockerJvmCtx.get("dockerJvmVersion"));

        Map<String, Object> dockerNativeCtx = templates.contextByTemplate.get("templates/svc-project/Dockerfile.native.tpl");
        assertEquals("1.6.0", dockerNativeCtx.get("dockerNativeVersion"));

        Map<String, Object> chartCtx = templates.contextByTemplate.get("templates/svc-project/Chart.yaml.tpl");
        assertEquals("0.50.0", chartCtx.get("helmVersion"));

        assertTrue(latest.requestedRepos.contains("onecx/onecx-quarkus3-parent"));
        assertTrue(latest.requestedRepos.contains("onecx/docker-quarkus-jvm"));
        assertTrue(latest.requestedRepos.contains("onecx/docker-quarkus-native"));
        assertTrue(latest.requestedRepos.contains("onecx/helm-quarkus-app"));

        assertTrue(Files.exists(root.resolve("src/main/resources/db/changeLog.xml")));
    }

    @Test
    void generateFallsBackToOldPomVariantsForNonSemverParent() throws Exception {
        GeneratorService service = new GeneratorService();
        CapturingTemplateService templates = new CapturingTemplateService();
        StubLatestVersionResolver latest = new StubLatestVersionResolver(Map.of(
                "onecx/onecx-quarkus3-parent", "release-latest"
        ));

        service.templates = templates;
        service.naming = new NamingService();
        service.buildService = new NoopBuildService();
        service.liquibase = new LiquibaseChangelogService();
        service.latestVersionResolver = latest;

        Path outputDir = Files.createTempDirectory("svc-generator-test-");
        service.generate(new CreateSvcRequest(
                "onecx-legacy-svc",
                "org.tkit.onecx",
                "onecx-legacy-svc",
                "org.tkit.onecx.legacy",
                outputDir,
                false
        ), null);

        Map<String, Object> pomCtx = templates.contextByTemplate.get("templates/svc-project/pom.xml.tpl");
        assertEquals("release-latest", pomCtx.get("parentVersion"));
        assertEquals("quarkus-junit5", pomCtx.get("junitArtifact"));
        assertEquals("quarkus-junit5-mockito", pomCtx.get("junitMockitoArtifact"));
        assertEquals("", pomCtx.get("packagingSection"));
    }

    private static final class CapturingTemplateService extends TemplateService {
        private final Map<String, Map<String, Object>> contextByTemplate = new HashMap<>();

        @Override
        public void renderToFile(Path templateDir, String resourcePath, Path target, Map<String, ?> ctx) {
            contextByTemplate.put(resourcePath, new HashMap<>(ctx));
        }
    }

    private static final class NoopBuildService extends BuildService {
        @Override
        public void runMavenBuild(Path projectPath) {
            // no-op in unit tests
        }
    }

    private static final class StubLatestVersionResolver extends LatestVersionResolver {
        private final Map<String, String> versions;
        private final List<String> requestedRepos = new ArrayList<>();

        private StubLatestVersionResolver(Map<String, String> versions) {
            this.versions = versions;
        }

        @Override
        public String resolveLatest(String ownerAndRepo, String fallback) {
            requestedRepos.add(ownerAndRepo);
            return versions.getOrDefault(ownerAndRepo, fallback);
        }

        @Override
        public ResolvedVersion resolveLatestWithSource(String ownerAndRepo, String fallback) {
            requestedRepos.add(ownerAndRepo);
            String value = versions.getOrDefault(ownerAndRepo, fallback);
            Source source = versions.containsKey(ownerAndRepo) ? Source.LATEST : Source.DEFAULT;
            return new ResolvedVersion(value, source);
        }
    }

    @Test
    void shouldUseCustomTemplateWhenPresent() throws Exception {

        TemplateService service = new TemplateService();

        Path templateDir = Files.createTempDirectory("templates");

        Path customTemplate = templateDir.resolve("svc-project/pom.xml.tpl");

        Files.createDirectories(customTemplate.getParent());

        Files.writeString(
                customTemplate,
                "<custom>{{name}}</custom>"
        );

        Path target = Files.createTempFile("pom", ".xml");

        service.renderToFile(
                templateDir,
                "templates/svc-project/pom.xml.tpl",
                target,
                Map.of("name", "demo")
        );

        String generated = Files.readString(target);

        assertEquals("<custom>demo</custom>", generated);
    }

    @Test
    void shouldFallbackToClasspathTemplateWhenCustomTemplateMissing() throws Exception {

        TemplateService service = new TemplateService();

        Path templateDir = Files.createTempDirectory("templates");

        Path target = Files.createTempFile("gitignore", ".txt");

        service.renderToFile(
                templateDir,
                "templates/svc-project/gitignore.tpl",
                target,
                Map.of()
        );

        assertTrue(Files.size(target) > 0);
    }

    @Test
    void shouldFallbackWhenTemplateDirectoryDoesNotExist() throws Exception {

        TemplateService service = new TemplateService();

        Path target = Files.createTempFile("gitignore", ".txt");

        service.renderToFile(
                Path.of("/tmp/not-existing-dir"),
                "templates/svc-project/gitignore.tpl",
                target,
                Map.of()
        );

        assertTrue(Files.exists(target));
        assertTrue(Files.size(target) > 0);
    }
}

