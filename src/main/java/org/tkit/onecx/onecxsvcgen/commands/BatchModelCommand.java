package org.tkit.onecx.onecxsvcgen.commands;

import org.tkit.onecx.onecxsvcgen.model.EntityDef;
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

@Command(name = "batch-model", description = "Generate multiple entities from a YAML model file")
public class BatchModelCommand implements Runnable {

    @Option(
            names = {"--model", "--file"},
            required = true,
            description = "YAML model file describing entities"
    )
    Path model;

    @Option(names = "--project", required = true, description = "Target generated service path")
    Path project;

    @Option(names = "--package", required = true, description = "Base Java package")
    String pkg;

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
    ModelParserService models;

    @Inject
    TemplateService templates;

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
        Path projectPath = project.toAbsolutePath().normalize();
        Path modelFile = model.toAbsolutePath().normalize();

        if (!Files.exists(modelFile)) {
            throw new IllegalArgumentException("Model file does not exist: " + modelFile);
        }
        if (!Files.isRegularFile(modelFile)) {
            throw new IllegalArgumentException("Model path is not a file: " + modelFile);
        }

        List<EntityDef> entities = models.parseEntitiesYaml(modelFile);
        String artifactId = projectPath.getFileName().toString();
        String scopePrefix = naming.scopePrefixFromArtifactId(artifactId);

        Path internalSpec = projectPath.resolve("src/main/openapi/" + artifactId + "-internal.yaml");
        Path externalSpec = projectPath.resolve("src/main/openapi/" + artifactId + "-external-v1.yaml");

        for (EntityDef entityDef : entities) {
            try {
                openApi.addOrUpdateEntity(
                        internalSpec,
                        externalSpec,
                        scopePrefix,
                        entityDef.name(),
                        entityDef.fields(),
                        entityDef.relations(),
                        entityDef.api()
                );

                Map<String, Object> ctx = new HashMap<>();

                String entity = entityDef.name();
                String entityField = naming.lowerCamel(entity);
                String resourcePath = entityDef.api().path() != null
                        ? entityDef.api().path()
                        : naming.pluralPath(entity);
                String resourceOperationPlural = naming.upperFirst(resourcePath.replace("-", ""));

                String baseTag = entityDef.api().tag() != null
                        ? entityDef.api().tag()
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
                ctx.put("entityImports", models.buildEntityImports(entityDef.fields()));
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
                ctx.put("externalMapperMappingImport", entityDef.api().expose() ? "import org.mapstruct.Mapping;\n" : "");
                ctx.put("externalMapperPageResultImports", entityDef.api().expose() ? "import " + models.generatedInternalModelPackage(pkg) + "." + entity + "PageResultDTO;\nimport org.tkit.quarkus.jpa.daos.PageResult;\n" : "");
                ctx.put("mapPageResultMethod", entityDef.api().expose() ? "\n    @Mapping(target = \"removeStreamItem\", ignore = true)\n    " + entity + "PageResultDTO mapPageResult(PageResult<" + entity + "> pageResult);" : "");
                ctx.put("modelPackage", models.modelPackage(pkg));
                ctx.put("daoPackage", models.daoPackage(pkg));
                ctx.put("domainServicePackage", models.domainServicePackage(pkg));

                ctx.put("controllerPackage", models.controllerPackage(pkg));
                ctx.put("mapperPackage", models.mapperPackage(pkg));

                ctx.put("externalControllerPackage", models.externalControllerPackage(pkg));
                ctx.put("externalMapperPackage", models.externalMapperPackage(pkg));

                ctx.put("fieldsDecl", models.buildFieldsDecl(entityDef.fields()));
                ctx.put("relationsDecl", models.buildRelationsDecl(entityDef.relations(), pkg));
                ctx.put("liquibaseColumns", models.buildLiquibaseColumns(entityDef.fields(), entityDef.relations()));
                ctx.put("findByCriteriaPredicates", models.buildFindByCriteriaPredicates(entity, entityDef.fields()));
                ctx.put("relationMappingMethods", models.buildRelationMappingMethods(entityDef.relations(), pkg));

                ctx.put("serviceRelationImports", models.buildServiceRelationImports(entityDef.relations(), pkg));
                ctx.put("relationDaoInjections", models.buildRelationDaoInjections(entityDef.relations()));
                ctx.put("relationCreateResolvers", models.buildRelationCreateResolvers(entityDef.relations()));
                ctx.put("relationUpdateResolvers", models.buildRelationUpdateResolvers(entityDef.relations()));

                // test code fragments (to be generated by ModelParserService)
                ctx.put("testCreateDtoBody", models.buildTestCreateDtoBody(entityDef.fields(), entityDef.relations(), entity + "DTO"));
                ctx.put("testUpdateDtoBody", models.buildTestUpdateDtoBody(entityDef.fields(), entityDef.relations(), entity + "DTO"));
                ctx.put("testSearchCriteriaBody", models.buildTestSearchCriteriaBody(entityDef.fields(), entity + "SearchCriteriaDTO"));
                ctx.put("testSearchSeedBody", models.buildTestSearchSeedBody(entityDef.fields()));
                ctx.put("testExternalSearchCriteriaBody", models.buildTestExternalSearchCriteriaBody(entityDef.fields(), entity + "SearchCriteriaDTOV1"));
                ctx.put(
                        "testInternalControllerAdditionalMethods",
                        models.buildInternalControllerAdditionalMethods(entity, resourcePath, entityDef.fields(), entityDef.relations())
                );
                ctx.put(
                        "testInternalControllerHelperMethods",
                        models.buildInternalControllerHelperMethods(entity, resourcePath, entityDef.fields(), entityDef.relations())
                );
                ctx.put(
                        "testExternalControllerAdditionalMethods",
                        models.buildExternalControllerAdditionalMethods(entity, resourcePath, entityDef.fields(), entityDef.relations())
                );
                ctx.put(
                        "testExternalControllerHelperMethods",
                        models.buildExternalControllerHelperMethods(entity, resourcePath, entityDef.fields(), entityDef.relations())
                );

                ctx.put("testEntityFieldsInit", models.buildTestEntityFieldsInit(entityDef.fields(), entityDef.relations()));
                ctx.put("testDtoFieldsInit", models.buildTestDtoFieldsInit(entityDef.fields(), entityDef.relations(), entity + "DTO"));
                ctx.put("testDtoUpdateFieldsInit", models.buildTestDtoUpdateFieldsInit(entityDef.fields(), entityDef.relations(), entity + "DTO"));

                ctx.put("testDtoAssertions", models.buildTestDtoAssertions(entityDef.fields(), entityDef.relations()));
                ctx.put("testExternalDtoAssertions", models.buildTestExternalDtoAssertions(entityDef.fields(), entityDef.relations()));
                ctx.put("testEntityAssertions", models.buildTestEntityAssertions(entityDef.fields(), entityDef.relations()));
                ctx.put("testUpdatedEntityAssertions", models.buildTestUpdatedEntityAssertions(entityDef.fields(), entityDef.relations()));

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
                        entityDef.api().expose()
                                ? "templates/entity/DAO.java.tpl"
                                : "templates/entity/NonRootDAO.java.tpl",
                        base.resolve("domain/daos/" + entity + "DAO.java"),
                        ctx
                );

                if (entityDef.api().expose()) {
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

                if (entityDef.api().expose()) {
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

                // TESTS generated from current structure

                // only for root entities with controllers
                if (entityDef.api().expose()) {
                    renderIfMissing(
                            "templates/test/AbstractTest.java.tpl",
                            testBase.resolve("AbstractTest.java"),
                            ctx
                    );

                    // Controller tests are always regenerated to keep generated fragments up-to-date
                    templates.renderToFile(
                            "templates/test/ControllerTest.java.tpl",
                            testBase.resolve("rs/internal/controllers/" + entity + "ControllerTest.java"),
                            ctx
                    );
                    templates.renderToFile(
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
                github.generate(projectPath, ctx);
            } catch (Exception e) {
                throw new RuntimeException("batch-model failed while generating entity: " + entityDef.name(), e);
            }
        }

        liquibase.ensureStructure(projectPath);

        String changelogFile = liquibase.hasIncludedChangelog(projectPath)
                ? liquibase.batchFileName()
                : liquibase.initialFileName();

        if (!liquibaseDiff) {
            Map<String, Object> changelogCtx = new HashMap<>();
            changelogCtx.put("liquibaseChangeSets", models.buildLiquibaseChangeSets(entities));

            templates.renderToFile(
                    "templates/entity/Liquibase-changeset.xml.tpl",
                    projectPath.resolve("src/main/resources/db/changelog/" + changelogFile),
                    changelogCtx
            );
            liquibase.registerInclude(projectPath, changelogFile);
        }

        System.out.println("✔ Generated " + entities.size() + " entities from model: " + modelFile);
        System.out.println("✔ Test skeletons generated for entities (if missing)");

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