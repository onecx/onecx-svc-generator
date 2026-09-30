package org.tkit.onecx.onecxsvcgen.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.tkit.onecx.onecxsvcgen.model.CreateSvcRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class GeneratorService {

    private static final String PARENT_REPO = "onecx/onecx-quarkus3-parent";
    private static final String DOCKER_JVM_REPO = "onecx/docker-quarkus-jvm";
    private static final String DOCKER_NATIVE_REPO = "onecx/docker-quarkus-native";
    private static final String HELM_REPO = "onecx/helm-quarkus-app";
    private static final String DEFAULT_PARENT_VERSION = "3.1.0";
    private static final String DEFAULT_DOCKER_VERSION = "1.4.0";
    private static final String DEFAULT_HELM_VERSION = "0.42.0";
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    @Inject
    TemplateService templates;

    @Inject
    NamingService naming;

    @Inject
    BuildService buildService;

    @Inject
    LiquibaseChangelogService liquibase;

    @Inject
    LatestVersionResolver latestVersionResolver;

    public Path generate(CreateSvcRequest request, Path templateDir) throws Exception {
        LatestVersionResolver.ResolvedVersion parentResolved = latestVersionResolver.resolveLatestWithSource(PARENT_REPO, DEFAULT_PARENT_VERSION);
        LatestVersionResolver.ResolvedVersion dockerJvmResolved = latestVersionResolver.resolveLatestWithSource(DOCKER_JVM_REPO, DEFAULT_DOCKER_VERSION);
        LatestVersionResolver.ResolvedVersion dockerNativeResolved = latestVersionResolver.resolveLatestWithSource(DOCKER_NATIVE_REPO, DEFAULT_DOCKER_VERSION);
        LatestVersionResolver.ResolvedVersion helmResolved = latestVersionResolver.resolveLatestWithSource(HELM_REPO, DEFAULT_HELM_VERSION);

        String parentVersion = parentResolved.version();
        String dockerJvmVersion = dockerJvmResolved.version();
        String dockerNativeVersion = dockerNativeResolved.version();
        String helmVersion = helmResolved.version();

        String resolvedArtifactId = sanitizeArtifactId(request.name(), request.artifactId());
        Path baseDir = (request.outputDir() != null ? request.outputDir() : Path.of(".")).toAbsolutePath().normalize();
        Path root = resolveProjectDir(baseDir, request.name());
        Files.createDirectories(root);

        String scopePrefix = naming.scopePrefixFromArtifactId(request.name());
        boolean useNewPom = isNewPomVersion(parentVersion);

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("name", request.name());
        ctx.put("projectName", request.name());
        ctx.put("artifactId", resolvedArtifactId);
        ctx.put("dbName", request.name().replace("-", "_"));
        ctx.put("groupId", request.groupId());
        ctx.put("package", request.pkg());
        ctx.put("packageName", request.pkg());
        ctx.put("basePackage", request.pkg());
        ctx.put("parentVersion", parentVersion);
        ctx.put("dockerJvmVersion", dockerJvmVersion);
        ctx.put("dockerNativeVersion", dockerNativeVersion);
        ctx.put("helmVersion", helmVersion);
        ctx.put("projectVersion", "999-SNAPSHOT");
        ctx.put("packagingSection", useNewPom ? "<packaging>quarkus</packaging>\n    " : "");
        ctx.put("junitArtifact", useNewPom ? "quarkus-junit" : "quarkus-junit5");
        ctx.put("junitMockitoArtifact", useNewPom ? "quarkus-junit-mockito" : "quarkus-junit5-mockito");
        ctx.put("scopePrefix", scopePrefix);
        ctx.put("generatedApiPackage", "gen." + request.pkg() + ".rs.external.v1");
        ctx.put("generatedModelPackage", "gen." + request.pkg() + ".rs.external.v1.model");
        ctx.put("generatedInternalApiPackage", "gen." + request.pkg() + ".rs.internal");
        ctx.put("generatedInternalModelPackage", "gen." + request.pkg() + ".rs.internal.model");

        templates.renderToFile(templateDir,"templates/svc-project/pom.xml.tpl", root.resolve("pom.xml"), ctx);
        templates.renderToFile(templateDir,"templates/svc-project/gitignore.tpl", root.resolve(".gitignore"), ctx);
        templates.renderToFile(templateDir,"templates/svc-project/application.properties.tpl", root.resolve("src/main/resources/application.properties"), ctx);
        templates.renderToFile(templateDir,"templates/svc-project/Dockerfile.jvm.tpl", root.resolve("src/main/docker/Dockerfile.jvm"), ctx);
        templates.renderToFile(templateDir,"templates/svc-project/Dockerfile.native.tpl", root.resolve("src/main/docker/Dockerfile.native"), ctx);
        templates.renderToFile(templateDir,"templates/svc-project/Chart.yaml.tpl", root.resolve("src/main/helm/Chart.yaml"), ctx);
        templates.renderToFile(templateDir,"templates/svc-project/values.yaml.tpl", root.resolve("src/main/helm/values.yaml"), ctx);
        templates.renderToFile(templateDir,"templates/entity/Liquibase-changelog.xml.tpl", root.resolve("src/main/resources/db/changeLog.xml"), ctx);

        Files.createDirectories(root.resolve("src/main/resources/db/changelog"));

        templates.renderToFile(templateDir,
                "templates/svc-project/openapi-skeleton.yaml.tpl",
                root.resolve("src/main/openapi/" + request.name() + "-internal.yaml"),
                ctx
        );
        templates.renderToFile(templateDir,
                "templates/svc-project/openapi-skeleton.yaml.tpl",
                root.resolve("src/main/openapi/" + request.name() + "-external-v1.yaml"),
                ctx
        );

        liquibase.ensureStructure(root);

        System.out.println("✔ Generated OneCX service: " + root);
        System.out.println("✔ Project name: " + request.name());
        System.out.println("✔ Artifact ID: " + resolvedArtifactId);
        System.out.println("✔ Scope prefix: " + scopePrefix);

        System.out.println("▶ Resolved generator versions:");
        System.out.println("  onecx-quarkus3-parent : " + formatResolved(parentResolved));
        System.out.println("  docker-quarkus-jvm    : " + formatResolved(dockerJvmResolved));
        System.out.println("  docker-quarkus-native : " + formatResolved(dockerNativeResolved));
        System.out.println("  helm-quarkus-app      : " + formatResolved(helmResolved));

        if (request.build()) {
            System.out.println("▶ Build requested, starting Maven build...");
            buildService.runMavenBuild(root);
        }

        return root;
    }

    private boolean isNewPomVersion(String version) {
        try {
            Matcher matcher = VERSION_PATTERN.matcher(version == null ? "" : version.trim());
            if (matcher.find()) {
                int major = Integer.parseInt(matcher.group(1));
                int minor = Integer.parseInt(matcher.group(2));
                int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
                int verNum = major * 10000 + minor * 100 + patch;
                return verNum >= (3 * 10000 + 100);
            }
        } catch (Exception ignored) {
            // fall through to false
        }
        return false;
    }

    private String sanitizeArtifactId(String projectName, String artifactId) {
        String raw = artifactId == null || artifactId.isBlank()
                ? projectName
                : artifactId;

        String clean = raw.toLowerCase().replaceAll("[^a-z0-9.-]", "-");
        String normalized = clean.replaceAll("-+", "-").replaceAll("^-|-$", "");

        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Could not derive artifactId from input");
        }

        return normalized;
    }

    private Path resolveProjectDir(Path baseDir, String projectName) {
        Path fileName = baseDir.getFileName();

        if (fileName != null && projectName.equals(fileName.toString())) {
            return baseDir;
        }

        return baseDir.resolve(projectName).toAbsolutePath().normalize();
    }

    private String formatResolved(LatestVersionResolver.ResolvedVersion resolved) {
        String suffix = resolved.source() == LatestVersionResolver.Source.LATEST ? "(latest)" : "(as default)";
        return resolved.version() + " " + suffix;
    }
}

