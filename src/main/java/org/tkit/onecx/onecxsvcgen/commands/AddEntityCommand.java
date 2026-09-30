package org.tkit.onecx.onecxsvcgen.commands;

import jakarta.inject.Inject;
import org.tkit.onecx.onecxsvcgen.model.ApiDef;
import org.tkit.onecx.onecxsvcgen.model.EntityDef;
import org.tkit.onecx.onecxsvcgen.model.FieldDef;
import org.tkit.onecx.onecxsvcgen.model.RelationDef;
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
        name = "add-entity",
        description = "Generate domain layer and update internal/external API contracts. " +
                "Root entities get CRUD in internal and read/search in external-v1; " +
                "child entities extend parent schemas."
)
public class AddEntityCommand implements Runnable {

    @Option(names = "--project", required = true, description = "Path to an existing generated service")
    Path project;

    @Option(names = "--package", required = true, description = "Base Java package")
    String pkg;

    @Option(names = "--entity", required = true, description = "Entity name")
    String entity;

    @Option(names = "--fields", split = ",", description = "Fields, e.g. name:String,price:BigDecimal")
    List<String> fieldsRaw;

    @Option(names = "--relations", split = ",", description = "Relations, e.g. category:ManyToOne:Category")
    List<String> relationsRaw;

    @Option(
            names = "--root",
            defaultValue = "true",
            arity = "1",
            description = "true = entity gets standalone API CRUD; false = child component extends parent schema only"
    )
    boolean root;

    @Option(names = "--api-parent", description = "Parent aggregate root name if --root=false")
    String apiParent;

    @Option(names = "--api-field", description = "Field name to add to the parent schema if --root=false")
    String apiField;

    @Option(
            names = "--api-parent-collection",
            defaultValue = "false",
            arity = "1",
            description = "true if parent field should be an array of the child DTO"
    )
    boolean apiParentCollection;

    @Option(names = "--api-path", description = "Override the resource path for root entities, e.g. chats")
    String apiPath;

    @Option(names = "--api-tag", description = "Override the external OpenAPI tag base for the entity")
    String apiTag;

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

            List<FieldDef> fields = models.parseFields(fieldsRaw);
            List<RelationDef> relations = models.parseRelations(relationsRaw);

            String projectName = projectPath.getFileName().toString();
            String scopePrefix = naming.scopePrefixFromArtifactId(projectName);

            ApiDef api = new ApiDef(root, apiParent, apiField, apiParentCollection, apiPath, apiTag);
            EntityDef entityDef = new EntityDef(entity, root, api, fields, relations);

            Path internalSpec = projectPath.resolve("src/main/openapi/" + projectName + "-internal.yaml");
            Path externalSpec = projectPath.resolve("src/main/openapi/" + projectName + "-external-v1.yaml");

            Path base = projectPath.resolve("src/main/java/" + pkg.replace('.', '/'));
            Path testBase = projectPath.resolve("src/test/java/" + pkg.replace('.', '/'));

            projectStructureService.ensureMainStructure(base, projectPath);
            projectStructureService.ensureTestStructure(testBase, projectPath);
            liquibase.ensureStructure(projectPath);

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

            String changelogFile = liquibase.entityFileName(entity);

            if (!liquibaseDiff) {
                Map<String, Object> changelogCtx = new HashMap<>();
                changelogCtx.put("liquibaseChangeSets", models.buildLiquibaseChangeSet(entity, fields, relations));

                templates.renderToFile( templateDir,
                        "templates/entity/Liquibase-changeset.xml.tpl",
                        projectPath.resolve("src/main/resources/db/changelog/" + changelogFile),
                        changelogCtx
                );

                liquibase.registerInclude(projectPath, changelogFile);
            }

            if (liquibaseDiff) {
                System.out.println("▶ Liquibase diff requested, generating changelog from db-diff profile...");
                buildService.runLiquibaseDiff(projectPath);
                liquibase.importDiffResult(projectPath, changelogFile);

                if (build) {
                    System.out.println("▶ Build requested, starting Maven build...");
                    buildService.runMavenBuild(projectPath);
                }
            } else if (build) {
                System.out.println("▶ Build requested, starting Maven build...");
                buildService.runMavenBuild(projectPath);
            }

            if (!Files.exists(projectPath.resolve(".github"))) {
                github.generate(projectPath, gitHubContextFactory.build(projectName, pkg, scopePrefix), templateDir);
            }

            templates.printTemplateSummary();

        } catch (Exception e) {
            throw new RuntimeException("add-entity failed", e);
        }
    }
}