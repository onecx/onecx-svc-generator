package org.tkit.onecx.onecxsvcgen.commands;

import jakarta.inject.Inject;
import org.tkit.onecx.onecxsvcgen.model.EntityDef;
import org.tkit.onecx.onecxsvcgen.service.BuildService;
import org.tkit.onecx.onecxsvcgen.service.EntityGenerationService;
import org.tkit.onecx.onecxsvcgen.service.GitHubActionsService;
import org.tkit.onecx.onecxsvcgen.service.GitHubContextFactory;
import org.tkit.onecx.onecxsvcgen.service.LiquibaseChangelogService;
import org.tkit.onecx.onecxsvcgen.service.ModelParserService;
import org.tkit.onecx.onecxsvcgen.service.NamingService;
import org.tkit.onecx.onecxsvcgen.service.ProjectStructureService;
import org.tkit.onecx.onecxsvcgen.service.TemplateService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Command(
        name = "batch-model",
        description = "Generate multiple entities from a YAML model definition"
)
public class BatchModelCommand implements Runnable {

    @Option(names = "--project", required = true, description = "Path to an existing generated service")
    Path project;

    @Option(names = "--package", required = true, description = "Base Java package")
    String pkg;

    @Option(names = "--model", required = true, description = "Path to YAML model file")
    Path model;

    @Option(
            names = "--build",
            defaultValue = "false",
            fallbackValue = "true",
            arity = "0..1",
            description = "Run 'mvn clean package -DskipTests' in the generated project after generation"
    )
    boolean build;

    @Option(
            names = "--liquibase-diff",
            defaultValue = "false",
            fallbackValue = "true",
            arity = "0..1",
            description = "Generate Liquibase changelog using Maven profile db-diff and import target/liquibase-diff-changeLog.xml"
    )
    boolean liquibaseDiff;

    @Option(
            names = { "--template-dir" },
            description = "Directory containing custom template overrides")
    Path templateDir;

    @Inject
    TemplateService templates;

    @Inject
    ModelParserService models;

    @Inject
    NamingService naming;

    @Inject
    BuildService buildService;

    @Inject
    LiquibaseChangelogService liquibase;

    @Inject
    GitHubActionsService github;

    @Inject
    GitHubContextFactory gitHubContextFactory;

    @Inject
    ProjectStructureService projectStructureService;

    @Inject
    EntityGenerationService entityGenerationService;

    @Override
    public void run() {
        try {
            templates.startTemplateSession(templateDir);
            Path projectPath = project.toAbsolutePath().normalize();
            Path modelPath = model.toAbsolutePath().normalize();

            List<EntityDef> entities = models.parseEntitiesYaml(modelPath);

            String projectName = projectPath.getFileName().toString();
            String scopePrefix = naming.scopePrefixFromArtifactId(projectName);

            Path internalSpec = projectPath.resolve("src/main/openapi/" + projectName + "-internal.yaml");
            Path externalSpec = projectPath.resolve("src/main/openapi/" + projectName + "-external-v1.yaml");

            Path base = projectPath.resolve("src/main/java/" + pkg.replace('.', '/'));
            Path testBase = projectPath.resolve("src/test/java/" + pkg.replace('.', '/'));

            projectStructureService.ensureMainStructure(base, projectPath);
            projectStructureService.ensureTestStructure(testBase, projectPath);
            liquibase.ensureStructure(projectPath);

            for (EntityDef entityDef : entities) {
                entityGenerationService.generateEntity(
                        projectPath,
                        pkg,
                        projectName,
                        scopePrefix,
                        internalSpec,
                        externalSpec,
                        entityDef,
                        templateDir
                );
            }

            if (liquibaseDiff) {
                System.out.println("▶ Liquibase diff requested, generating changelog from db-diff profile...");
                buildService.runLiquibaseDiff(projectPath);

                String changelogFile = liquibase.entityFileName("model");
                liquibase.importDiffResult(projectPath, changelogFile);
            } else {
                for (EntityDef entityDef : entities) {
                    String changelogFile = liquibase.entityFileName(entityDef.name());

                    Map<String, Object> changelogCtx = new HashMap<>();
                    changelogCtx.put(
                            "liquibaseChangeSets",
                            models.buildLiquibaseChangeSet(
                                    entityDef.name(),
                                    entityDef.fields(),
                                    entityDef.relations()
                            )
                    );

                    templates.renderToFile( templateDir,
                            "templates/entity/Liquibase-changeset.xml.tpl",
                            projectPath.resolve("src/main/resources/db/changelog/" + changelogFile),
                            changelogCtx
                    );

                    liquibase.registerInclude(projectPath, changelogFile);
                }
            }

            if (!Files.exists(projectPath.resolve(".github"))) {
                github.generate(projectPath, gitHubContextFactory.build(projectName, pkg, scopePrefix), templateDir);
            }

            templates.printTemplateSummary();

            if (build) {
                System.out.println("▶ Build requested, starting Maven build...");
                buildService.runMavenBuild(projectPath);
            }

            System.out.println("✔ Generated model from: " + modelPath);
            System.out.println("✔ Project: " + projectPath);
            System.out.println("✔ Entities: " + entities.size());

        } catch (Exception e) {
            throw new RuntimeException("batch-model failed", e);
        }
    }
}