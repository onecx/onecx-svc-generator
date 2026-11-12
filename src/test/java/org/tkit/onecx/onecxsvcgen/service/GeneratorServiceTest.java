package org.tkit.onecx.onecxsvcgen.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tkit.onecx.onecxsvcgen.model.CreateSvcRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorServiceTest {

    @Test
    void generateCopiesAndEnablesBundledHook(@TempDir Path outputDir) throws Exception {
        byte[] content = """
                #!/bin/sh
                [ -z "${CI:-}" ] && [ -z "${FORCE_CI_COMMIT_HOOKS:-}" ]
                """.getBytes(StandardCharsets.UTF_8);
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(hookClassLoader(content, original));
        try {
            GeneratorService service = new GeneratorService();
            service.templates = new TemplateService();
            service.naming = new NamingService();
            service.liquibase = new LiquibaseChangelogService();
            service.latestVersionResolver = new StubLatestVersionResolver(Map.of());

            Path root = service.generate(new CreateSvcRequest(
                    "orders-svc", "org.tkit.onecx", null, "org.tkit.onecx.orders", outputDir, false
            ), null);

            Path hook = root.resolve(".githooks/pre-commit");
            assertArrayEquals(content, Files.readAllBytes(hook));
            assertTrue(Files.isExecutable(hook));
            ProcessBuilder commit = git(root, "-c", "user.name=Generator Test",
                    "-c", "user.email=generator-test@example.invalid", "-c", "commit.gpgsign=false",
                    "commit", "--quiet", "--allow-empty", "-m", "test: verify hook activation",
                    "-m", "Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>");
            commit.environment().remove("CI");
            commit.environment().remove("FORCE_CI_COMMIT_HOOKS");
            assertEquals(0, commit.start().waitFor(), "Local commits should be allowed");
            commit.environment().put("CI", "true");
            assertEquals(1, commit.start().waitFor(), "The enabled hook must reject CI commits");
            commit.environment().remove("CI");
            commit.environment().put("FORCE_CI_COMMIT_HOOKS", "1");
            assertEquals(1, commit.start().waitFor(), "The enabled hook must reject forced commits");
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void hookSetupIsRepeatableAndDoesNotChangeParentRepository(@TempDir Path parent) throws Exception {
        assertEquals(0, git(parent, "init", "--quiet").start().waitFor());
        assertEquals(0, git(parent, "config", "--local", "core.hooksPath", "parent-hooks").start().waitFor());
        Path project = Files.createDirectory(parent.resolve("orders-svc"));
        byte[] content = "#!/bin/sh\nexit 0\n".getBytes(StandardCharsets.UTF_8);
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(hookClassLoader(content, original));
        try {
            GeneratorService service = new GeneratorService();
            service.writePreCommitHook(project);
            Files.writeString(project.resolve(".githooks/pre-commit"), "outdated hook");
            service.writePreCommitHook(project);

            assertArrayEquals(content, Files.readAllBytes(project.resolve(".githooks/pre-commit")));
            assertTrue(Files.isDirectory(project.resolve(".git")));
            Process config = git(parent, "config", "--local", "--get", "core.hooksPath")
                    .redirectOutput(ProcessBuilder.Redirect.PIPE).start();
            assertEquals("parent-hooks", new String(config.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim());
            assertEquals(0, config.waitFor());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void missingBundledHookWarnsWithoutChangingProject(@TempDir Path project) throws Exception {
        Path output = project.resolve(".githooks/pre-commit");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "existing hook");
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        PrintStream originalErr = System.err;
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        Thread.currentThread().setContextClassLoader(hookClassLoader(null, original));
        try (PrintStream captured = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            System.setErr(captured);
            new GeneratorService().writePreCommitHook(project);

            assertEquals("existing hook", Files.readString(output));
            assertFalse(Files.exists(project.resolve(".git")));
            assertTrue(errors.toString(StandardCharsets.UTF_8).contains("Pre-commit hook not bundled"));
        } finally {
            System.setErr(originalErr);
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    void hookSetupReportsGitFailure(@TempDir Path project) throws Exception {
        Files.writeString(project.resolve(".git"), "invalid git directory");
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(hookClassLoader(
                "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8), original));
        try {
            IOException error = assertThrows(IOException.class,
                    () -> new GeneratorService().writePreCommitHook(project));
            assertTrue(error.getMessage().contains("git config --local core.hooksPath .githooks failed"));
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    private ClassLoader hookClassLoader(byte[] content, ClassLoader parent) {
        return new ClassLoader(parent) {
            @Override
            public InputStream getResourceAsStream(String name) {
                if ("hooks/pre-commit".equals(name)) {
                    return content == null ? null : new ByteArrayInputStream(content);
                }
                return super.getResourceAsStream(name);
            }
        };
    }

    private ProcessBuilder git(Path directory, String... arguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        return new ProcessBuilder(command).directory(directory.toFile()).inheritIO();
    }

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
                "orders-svc",
                "org.tkit.onecx",
                "orders-svc",
                "org.tkit.onecx.orders",
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

    @Test
    void generateUsesCompleteNamesAndPreservesExplicitCoordinates() throws Exception {
        for (String name : List.of("orders-svc", "onecx-orders-svc", "acme-orders-svc")) {
            GeneratorService service = new GeneratorService();
            CapturingTemplateService templates = new CapturingTemplateService();
            service.templates = templates;
            service.naming = new NamingService();
            service.buildService = new NoopBuildService();
            service.liquibase = new LiquibaseChangelogService();
            service.latestVersionResolver = new StubLatestVersionResolver(Map.of());

            Path outputDir = Files.createTempDirectory("svc-generator-naming-test-");
            service.generate(new CreateSvcRequest(
                    name,
                    "com.customer",
                    null,
                    "com.customer.orders",
                    outputDir,
                    false
            ), null);

            Map<String, Object> pomCtx = templates.contextByTemplate.get("templates/svc-project/pom.xml.tpl");
            Map<String, Object> applicationCtx = templates.contextByTemplate.get(
                    "templates/svc-project/application.properties.tpl"
            );
            assertEquals(name, pomCtx.get("artifactId"));
            assertEquals("com.customer", pomCtx.get("groupId"));
            assertEquals("com.customer.orders", pomCtx.get("package"));
            assertEquals(name, applicationCtx.get("name"));
            assertEquals(name, applicationCtx.get("artifactId"));
            assertEquals(name.replace("-", "_"), applicationCtx.get("dbName"));
        }
    }

    @Test
    void generatePreservesExplicitArtifactId() throws Exception {
        GeneratorService service = new GeneratorService();
        CapturingTemplateService templates = new CapturingTemplateService();
        service.templates = templates;
        service.naming = new NamingService();
        service.buildService = new NoopBuildService();
        service.liquibase = new LiquibaseChangelogService();
        service.latestVersionResolver = new StubLatestVersionResolver(Map.of());

        service.generate(new CreateSvcRequest(
                "orders-svc",
                "com.customer",
                "customer-orders",
                "com.customer.orders",
                Files.createTempDirectory("svc-generator-artifact-test-"),
                false
        ), null);

        Map<String, Object> pomCtx = templates.contextByTemplate.get("templates/svc-project/pom.xml.tpl");
        Map<String, Object> applicationCtx = templates.contextByTemplate.get(
                "templates/svc-project/application.properties.tpl"
        );
        assertEquals("customer-orders", pomCtx.get("artifactId"));
        assertEquals("customer-orders", applicationCtx.get("artifactId"));
        assertEquals("com.customer", pomCtx.get("groupId"));
        assertEquals("com.customer.orders", pomCtx.get("package"));
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

