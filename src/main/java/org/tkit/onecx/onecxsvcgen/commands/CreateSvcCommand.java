package org.tkit.onecx.onecxsvcgen.commands;

import org.tkit.onecx.onecxsvcgen.service.BuildService;
import org.tkit.onecx.onecxsvcgen.service.LiquibaseChangelogService;
import org.tkit.onecx.onecxsvcgen.service.NamingService;
import org.tkit.onecx.onecxsvcgen.service.TemplateService;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Command(name = "create-svc", description = "Generate a full OneCX-compliant Quarkus backend service")
public class CreateSvcCommand implements Runnable {

    @Option(names = "--name", required = true, description = "Artifact/repository name, e.g. onecx-demo-svc")
    String name;

    @Option(names = "--group", defaultValue = "org.tkit.onecx", description = "Maven groupId")
    String group;

    @Option(names = "--package", required = true, description = "Base Java package")
    String pkg;

    @Option(names = "--parent-version", description = "onecx-quarkus3-parent version; if omitted defaults to 3.1.0")
    String parentVersion;

    @Option(names = "--output-dir", description = "Directory where the service project should be generated")
    Path outputDir;

    @Option(
            names = "--build",
            defaultValue = "false",
            fallbackValue = "true",
            arity = "0..1",
            description = "Run 'mvn clean package -DskipTests' in the generated project after generation"
    )
    boolean build;

    @Inject
    TemplateService templates;

    @Inject
    NamingService naming;

    @Inject
    BuildService buildService;

    @Inject
    LiquibaseChangelogService liquibase;

    @Override
    public void run() {
        try {
            boolean parentProvided = parentVersion != null && !parentVersion.isBlank();
            if (!parentProvided) {
                // when parent version is not provided, default to the current supported baseline
                parentVersion = "3.1.0";
            }

            // decide whether to apply new POM changes based on parent version
            // If the resolved or provided parent version is >= 3.1.0 we enable the new POM layout;
            // otherwise keep the legacy layout.
            boolean useNewPom = false;
            try {
                String v = parentVersion.trim();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?").matcher(v);
                if (m.find()) {
                    int major = Integer.parseInt(m.group(1));
                    int minor = Integer.parseInt(m.group(2));
                    int patch = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
                    int verNum = major * 10000 + minor * 100 + patch; // e.g. 3.1.0 -> 30100
                    useNewPom = verNum >= (3 * 10000 + 1 * 100 + 0);
                }
            } catch (Exception ignore) {
                useNewPom = false;
            }

            Path baseDir = (outputDir != null ? outputDir : Path.of(".")).toAbsolutePath().normalize();
            Path root = baseDir.resolve(name).toAbsolutePath().normalize();
            Files.createDirectories(root);

            String scopePrefix = naming.scopePrefixFromArtifactId(name);

            Map<String, Object> ctx = new HashMap<>();
            ctx.put("name", name);
            ctx.put("dbName", name.replace("-", "_"));
            ctx.put("group", group);
            ctx.put("package", pkg);
            ctx.put("parentVersion", parentVersion);
            // project version for generated POMs (user requested override for all versions)
            ctx.put("projectVersion", "999-SNAPSHOT");
            // packaging section to insert under <version> when using new POM layout
            ctx.put("packagingSection", useNewPom ? "<packaging>quarkus</packaging>\n    " : "");
            // junit artifact ids depend on parent version
            ctx.put("junitArtifact", useNewPom ? "quarkus-junit" : "quarkus-junit5");
            ctx.put("junitMockitoArtifact", useNewPom ? "quarkus-junit-mockito" : "quarkus-junit5-mockito");
            ctx.put("scopePrefix", scopePrefix);

            ctx.put("generatedApiPackage", "gen." + pkg + ".rs.external.v1");
            ctx.put("generatedModelPackage", "gen." + pkg + ".rs.external.v1.model");
            ctx.put("generatedInternalApiPackage", "gen." + pkg + ".rs.internal");
            ctx.put("generatedInternalModelPackage", "gen." + pkg + ".rs.internal.model");

            templates.renderToFile("templates/svc-project/pom.xml.tpl", root.resolve("pom.xml"), ctx);
            templates.renderToFile("templates/svc-project/gitignore.tpl", root.resolve(".gitignore"), ctx);
            templates.renderToFile("templates/svc-project/application.properties.tpl", root.resolve("src/main/resources/application.properties"), ctx);
            templates.renderToFile("templates/svc-project/Dockerfile.jvm.tpl", root.resolve("src/main/docker/Dockerfile.jvm"), ctx);
            templates.renderToFile("templates/svc-project/Dockerfile.native.tpl", root.resolve("src/main/docker/Dockerfile.native"), ctx);
            templates.renderToFile("templates/svc-project/Chart.yaml.tpl", root.resolve("src/main/helm/Chart.yaml"), ctx);
            templates.renderToFile("templates/svc-project/values.yaml.tpl", root.resolve("src/main/helm/values.yaml"), ctx);
            templates.renderToFile("templates/entity/Liquibase-changelog.xml.tpl", root.resolve("src/main/resources/db/changeLog.xml"), ctx);

            Files.createDirectories(root.resolve("src/main/resources/db/changelog"));

            templates.renderToFile(
                    "templates/svc-project/openapi-skeleton.yaml.tpl",
                    root.resolve("src/main/openapi/" + name + "-internal.yaml"),
                    ctx
            );
            templates.renderToFile(
                    "templates/svc-project/openapi-skeleton.yaml.tpl",
                    root.resolve("src/main/openapi/" + name + "-external-v1.yaml"),
                    ctx
            );

            liquibase.ensureStructure(root);

            System.out.println("✔ Generated OneCX service: " + root);
            System.out.println("✔ Parent version: " + parentVersion);
            System.out.println("✔ Scope prefix: " + scopePrefix);

            if (build) {
                System.out.println("▶ Build requested, starting Maven build...");
                buildService.runMavenBuild(root);
            }
        } catch (Exception e) {
            throw new RuntimeException("create-svc failed", e);
        }
    }
}