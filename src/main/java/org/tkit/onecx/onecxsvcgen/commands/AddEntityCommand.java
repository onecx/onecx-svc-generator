package org.tkit.onecx.onecxsvcgen.commands;

import org.tkit.onecx.onecxsvcgen.model.ApiDef;
import org.tkit.onecx.onecxsvcgen.model.FieldDef;
import org.tkit.onecx.onecxsvcgen.model.RelationDef;
import org.tkit.onecx.onecxsvcgen.service.BuildService;
import org.tkit.onecx.onecxsvcgen.service.LiquibaseChangelogService;
import org.tkit.onecx.onecxsvcgen.service.ModelParserService;
import org.tkit.onecx.onecxsvcgen.service.NamingService;
import org.tkit.onecx.onecxsvcgen.service.OpenApiService;
import org.tkit.onecx.onecxsvcgen.service.TemplateService;
import org.tkit.onecx.onecxsvcgen.service.GitHubActionsService;
import jakarta.inject.Inject;
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

    @Inject
    TemplateService templates;

    @Inject
    ModelParserService models;

    @Inject
    NamingService naming;

    @Inject
    OpenApiService openApi;

    @Inject
    BuildService buildService;

    @Inject
    LiquibaseChangelogService liquibase;

    @Inject
    GitHubActionsService github;

    @Override
    public void run() {
        try {
            Path projectPath = project.toAbsolutePath().normalize();

            List<FieldDef> fields = models.parseFields(fieldsRaw);
            List<RelationDef> relations = models.parseRelations(relationsRaw);

            String artifactId = projectPath.getFileName().toString();
            String scopePrefix = naming.scopePrefixFromArtifactId(artifactId);

            ApiDef api = new ApiDef(root, apiParent, apiField, apiParentCollection, apiPath, apiTag);

            Path internalSpec = projectPath.resolve("src/main/openapi/" + artifactId + "-internal.yaml");
            Path externalSpec = projectPath.resolve("src/main/openapi/" + artifactId + "-external-v1.yaml");

            openApi.addOrUpdateEntity(
                    internalSpec,
                    externalSpec,
                    scopePrefix,
                    entity,
                    fields,
                    relations,
                    api
            );

            Map<String, Object> ctx = new HashMap<>();

            String entityField = naming.lowerCamel(entity);
            String resourcePath = api.path() != null ? api.path() : naming.pluralPath(entity);
            String resourceOperationPlural = naming.upperFirst(resourcePath.replace("-", ""));

            String baseTag = api.tag() != null
                    ? api.tag()
                    : naming.lowerCamel(resourcePath.replace("-", ""));

            String internalTag = baseTag.endsWith("Internal")
                    ? baseTag
                    : baseTag + "Internal";

            String internalApiInterface = naming.apiInterfaceName(internalTag);
            String externalApiInterface = naming.upperFirst(baseTag) + "V1Api";

            ctx.put("package", pkg);
            ctx.put("entity", entity);
            ctx.put("entityField", entityField);
            ctx.put("resourcePath", resourcePath);
            ctx.put("resourceOperationPlural", resourceOperationPlural);
            ctx.put("tableName", models.tableName(entity));
            ctx.put("entityImports", models.buildEntityImports(fields));
            ctx.put("scopePrefix", scopePrefix);

            // INTERNAL contract bindings
            ctx.put("resourceTag", internalTag);
            ctx.put("generatedApiPackage", models.generatedInternalApiPackage(pkg));
            ctx.put("generatedModelPackage", models.generatedInternalModelPackage(pkg));
            ctx.put("generatedApiInterface", internalApiInterface);
            ctx.put("generatedDto", entity + "DTO");
            ctx.put("generatedInternalSearchCriteria", entity + "SearchCriteriaDTO");
            ctx.put("generatedPageResultDto", entity + "PageResultDTO");

            // EXTERNAL contract bindings
            ctx.put("generatedExternalApiPackage", models.generatedApiPackage(pkg));
            ctx.put("generatedExternalModelPackage", models.generatedModelPackage(pkg));
            ctx.put("generatedExternalApiInterface", externalApiInterface);
            ctx.put("generatedExternalDto", entity + "DTOV1");
            ctx.put("generatedExternalSearchCriteria", entity + "SearchCriteriaDTOV1");
            ctx.put("externalOperationSuffix", "V1");
            ctx.put("externalMapperMappingImport", root ? "import org.mapstruct.Mapping;\n" : "");
            ctx.put("externalMapperPageResultImports", root ? "import " + models.generatedInternalModelPackage(pkg) + "." + entity + "PageResultDTO;\nimport org.tkit.quarkus.jpa.daos.PageResult;\n" : "");
            ctx.put("mapPageResultMethod", root ? "\n    @Mapping(target = \"removeStreamItem\", ignore = true)\n    " + entity + "PageResultDTO mapPageResult(PageResult<" + entity + "> pageResult);" : "");
            ctx.put("modelPackage", models.modelPackage(pkg));
            ctx.put("daoPackage", models.daoPackage(pkg));
            ctx.put("domainServicePackage", models.domainServicePackage(pkg));

            ctx.put("controllerPackage", models.controllerPackage(pkg));
            ctx.put("mapperPackage", models.mapperPackage(pkg));

            ctx.put("externalControllerPackage", models.externalControllerPackage(pkg));
            ctx.put("externalMapperPackage", models.externalMapperPackage(pkg));

            ctx.put("fieldsDecl", models.buildFieldsDecl(fields));
            ctx.put("relationsDecl", models.buildRelationsDecl(relations, pkg));
            ctx.put("liquibaseColumns", models.buildLiquibaseColumns(fields, relations));
            ctx.put("findByCriteriaPredicates", models.buildFindByCriteriaPredicates(entity, fields));
            ctx.put("relationMappingMethods", models.buildRelationMappingMethods(relations, pkg));

            ctx.put("serviceRelationImports", models.buildServiceRelationImports(relations, pkg));
            ctx.put("relationDaoInjections", models.buildRelationDaoInjections(relations));
            ctx.put("relationCreateResolvers", models.buildRelationCreateResolvers(relations));
            ctx.put("relationUpdateResolvers", models.buildRelationUpdateResolvers(relations));

            // test code fragments
            ctx.put("testCreateDtoBody", models.buildTestCreateDtoBody(fields, relations, entity + "DTO"));
            ctx.put("testUpdateDtoBody", models.buildTestUpdateDtoBody(fields, relations, entity + "DTO"));
            ctx.put("testSearchCriteriaBody", models.buildTestSearchCriteriaBody(fields, entity + "SearchCriteriaDTO"));
            ctx.put("testSearchSeedBody", models.buildTestSearchSeedBody(fields));
            ctx.put("testExternalSearchCriteriaBody", models.buildTestExternalSearchCriteriaBody(fields, entity + "SearchCriteriaDTOV1"));
            ctx.put(
                    "testInternalControllerAdditionalMethods",
                    models.buildInternalControllerAdditionalMethods(entity, resourcePath, fields, relations)
            );
            ctx.put(
                    "testInternalControllerHelperMethods",
                    models.buildInternalControllerHelperMethods(entity, resourcePath, fields, relations)
            );
            ctx.put(
                    "testExternalControllerAdditionalMethods",
                    models.buildExternalControllerAdditionalMethods(entity, resourcePath, fields, relations)
            );
            ctx.put(
                    "testExternalControllerHelperMethods",
                    models.buildExternalControllerHelperMethods(entity, resourcePath, fields, relations)
            );

            ctx.put("testEntityFieldsInit", models.buildTestEntityFieldsInit(fields, relations));
            ctx.put("testDtoFieldsInit", models.buildTestDtoFieldsInit(fields, relations, entity + "DTO"));
            ctx.put("testDtoUpdateFieldsInit", models.buildTestDtoUpdateFieldsInit(fields, relations, entity + "DTO"));

            ctx.put("testDtoAssertions", models.buildTestDtoAssertions(fields, relations));
            ctx.put("testExternalDtoAssertions", models.buildTestExternalDtoAssertions(fields, relations));
            ctx.put("testEntityAssertions", models.buildTestEntityAssertions(fields, relations));
            ctx.put("testUpdatedEntityAssertions", models.buildTestUpdatedEntityAssertions(fields, relations));

            Path base = projectPath.resolve("src/main/java/" + pkg.replace('.', '/'));
            Path testBase = projectPath.resolve("src/test/java/" + pkg.replace('.', '/'));

            ensureMainStructure(base, projectPath);
            ensureTestStructure(testBase, projectPath);

            templates.renderToFile(
                    "templates/entity/Entity.java.tpl",
                    base.resolve("domain/models/" + entity + ".java"),
                    ctx
            );
            templates.renderToFile(
                    root
                            ? "templates/entity/DAO.java.tpl"
                            : "templates/entity/NonRootDAO.java.tpl",
                    base.resolve("domain/daos/" + entity + "DAO.java"),
                    ctx
            );

            if (root) {
                templates.renderToFile(
                        "templates/entity/Service.java.tpl",
                        base.resolve("domain/services/" + entity + "Service.java"),
                        ctx
                );
            }

            // INTERNAL runtime boundary
            templates.renderToFile(
                    "templates/entity/Mapper.java.tpl",
                    base.resolve("rs/internal/mappers/" + entity + "Mapper.java"),
                    ctx
            );

            // INTERNAL exception mapper (standalone)
            renderIfMissing(
                    "templates/entity/InternalExceptionMapper.java.tpl",
                    base.resolve("rs/internal/mappers/InternalExceptionMapper.java"),
                    ctx
            );

            // EXTERNAL runtime boundary
            templates.renderToFile(
                    "templates/entity/ExternalMapper.java.tpl",
                    base.resolve("rs/external/v1/mappers/" + entity + "Mapper.java"),
                    ctx
            );
            renderIfMissing(
                    "templates/entity/ExternalExceptionMapper.java.tpl",
                    base.resolve("rs/external/v1/mappers/ExternalExceptionMapper.java"),
                    ctx
            );

            if (root) {
                templates.renderToFile(
                        "templates/entity/Controller.java.tpl",
                        base.resolve("rs/internal/controllers/" + entity + "Controller.java"),
                        ctx
                );
                templates.renderToFile(
                        "templates/entity/ExternalController.java.tpl",
                        base.resolve("rs/external/v1/controllers/" + entity + "Controller.java"),
                        ctx
                );
            }

            // TESTS

            // always

            // only for root entities with controllers
            if (root) {
                renderIfMissing(
                        "templates/test/AbstractTest.java.tpl",
                        testBase.resolve("AbstractTest.java"),
                        ctx
                );

                renderIfMissing(
                        "templates/test/ControllerTest.java.tpl",
                        testBase.resolve("rs/internal/controllers/" + entity + "ControllerTest.java"),
                        ctx
                );
                renderIfMissing(
                        "templates/test/ExternalControllerTest.java.tpl",
                        testBase.resolve("rs/external/v1/controllers/" + entity + "ControllerTest.java"),
                        ctx
                );

                renderIfMissing(
                        "templates/test/ControllerIT.java.tpl",
                        testBase.resolve("rs/internal/controllers/" + entity + "ControllerIT.java"),
                        ctx
                );
                renderIfMissing(
                        "templates/test/ExternalControllerIT.java.tpl",
                        testBase.resolve("rs/external/v1/controllers/" + entity + "ControllerIT.java"),
                        ctx
                );
            }

            liquibase.ensureStructure(projectPath);

            String changelogFile = liquibase.entityFileName(entity);

            if (!liquibaseDiff) {
                Map<String, Object> changelogCtx = new HashMap<>();
                changelogCtx.put("liquibaseChangeSets", models.buildLiquibaseChangeSet(entity, fields, relations));

                templates.renderToFile(
                        "templates/entity/Liquibase-changeset.xml.tpl",
                        projectPath.resolve("src/main/resources/db/changelog/" + changelogFile),
                        changelogCtx
                );
                liquibase.registerInclude(projectPath, changelogFile);
            }

            System.out.println("✔ Generated domain layer for: " + entity);
            if (root) {
                System.out.println("✔ Updated internal API/runtime (CRUD + search) and external-v1 API/runtime (get + search) for: " + entity);
                System.out.println("✔ Generated controller, mapper and service tests for root entity: " + entity);
            } else {
                System.out.println(
                        "✔ Added component schema " + entity + " to parent API " + apiParent
                                + " in internal and external-v1 contracts. No standalone CRUD paths created."
                );
                System.out.println("✔ Generated mapper and service tests for non-root entity: " + entity);
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
                github.generate(projectPath, ctx);
            }

        } catch (Exception e) {
            throw new RuntimeException("add-entity failed", e);
        }
    }

    private void ensureMainStructure(Path base, Path projectPath) throws Exception {
        Files.createDirectories(base.resolve("domain/models"));
        Files.createDirectories(base.resolve("domain/daos"));
        Files.createDirectories(base.resolve("domain/services"));
        // no shared common mapper; internal/external mappers are standalone
        Files.createDirectories(base.resolve("rs/internal/controllers"));
        Files.createDirectories(base.resolve("rs/internal/mappers"));
        Files.createDirectories(base.resolve("rs/external/v1/controllers"));
        Files.createDirectories(base.resolve("rs/external/v1/mappers"));
        Files.createDirectories(projectPath.resolve("src/main/resources/db/changelog"));
    }

    private void ensureTestStructure(Path testBase, Path projectPath) throws Exception {
        Files.createDirectories(testBase.resolve("rs/internal/controllers"));
        Files.createDirectories(testBase.resolve("rs/external/v1/controllers"));

        createFileIfMissing(projectPath.resolve("src/test/resources/application.properties"), "");
    }

    private void renderIfMissing(String template, Path target, Map<String, Object> ctx) throws Exception {
        if (!Files.exists(target)) {
            templates.renderToFile(template, target, ctx);
        }
    }

    private void createFileIfMissing(Path file, String content) throws Exception {
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        }
    }
}