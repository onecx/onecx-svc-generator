package org.tkit.onecx.onecxsvcgen.commands;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tkit.onecx.onecxsvcgen.service.BuildService;
import org.tkit.onecx.onecxsvcgen.service.EntityContextFactory;
import org.tkit.onecx.onecxsvcgen.service.EntityGenerationService;
import org.tkit.onecx.onecxsvcgen.service.EntityTemplateWriter;
import org.tkit.onecx.onecxsvcgen.service.GenerationLogService;
import org.tkit.onecx.onecxsvcgen.service.GitHubActionsService;
import org.tkit.onecx.onecxsvcgen.service.GitHubContextFactory;
import org.tkit.onecx.onecxsvcgen.service.GeneratorService;
import org.tkit.onecx.onecxsvcgen.service.LatestVersionResolver;
import org.tkit.onecx.onecxsvcgen.service.LiquibaseChangelogService;
import org.tkit.onecx.onecxsvcgen.service.ModelParserService;
import org.tkit.onecx.onecxsvcgen.service.NamingService;
import org.tkit.onecx.onecxsvcgen.service.OpenApiService;
import org.tkit.onecx.onecxsvcgen.service.ParentMapperSyncService;
import org.tkit.onecx.onecxsvcgen.service.ProjectStructureService;
import org.tkit.onecx.onecxsvcgen.service.TemplateService;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UnprefixedProjectCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void createSvcThenAddEntityUsesUnprefixedOpenApiMetadataAndCustomerPackage() throws Exception {
        Path project = createSvc("orders-svc");
        AddEntityCommand command = addEntityCommand(project);
        command.entity = "Product";
        command.fieldsRaw = java.util.List.of("name:String");
        command.root = true;
        command.run();

        assertGeneratedProjectIsConsistent(project, "Product");
    }

    @Test
    void createSvcThenBatchModelUsesUnprefixedOpenApiMetadataAndCustomerPackage() throws Exception {
        Path project = createSvc("orders-svc");
        Path model = tempDir.resolve("orders-model.yaml");
        Files.writeString(model, """
                entities:
                  - name: Shipment
                    aggregateRoot: true
                    fields:
                      - trackingCode:String
                """);

        BatchModelCommand command = new BatchModelCommand();
        command.project = project;
        command.pkg = "com.customer.orders";
        command.model = model;
        configure(command, "templates", new TemplateService());
        command.models = new ModelParserService();
        command.naming = new NamingService();
        command.buildService = new BuildService();
        command.liquibase = new LiquibaseChangelogService();
        command.github = new GitHubActionsService();
        command.gitHubContextFactory = new GitHubContextFactory();
        command.projectStructureService = new ProjectStructureService();
        command.entityGenerationService = entityGenerationService(new TemplateService());
        command.run();

        assertGeneratedProjectIsConsistent(project, "Shipment");
    }

    private Path createSvc(String name) throws Exception {
        TemplateService templates = new TemplateService();
        NamingService naming = new NamingService();
        GeneratorService generator = new GeneratorService();
        configure(generator, "templates", templates);
        configure(generator, "naming", naming);
        configure(generator, "buildService", new BuildService());
        configure(generator, "liquibase", new LiquibaseChangelogService());
        configure(generator, "latestVersionResolver", new StubLatestVersionResolver());

        CreateSvcCommand command = new CreateSvcCommand();
        command.name = name;
        command.groupId = "com.customer";
        command.pkg = "com.customer.orders";
        command.artifactId = null;
        command.outputDir = tempDir;
        command.generatorService = generator;
        command.templates = templates;
        command.run();

        Path project = tempDir.resolve(name);
        Files.createDirectories(project.resolve(".github"));
        return project;
    }

    private AddEntityCommand addEntityCommand(Path project) throws Exception {
        TemplateService templates = new TemplateService();
        AddEntityCommand command = new AddEntityCommand();
        command.project = project;
        command.pkg = "com.customer.orders";
        configure(command, "templates", templates);
        command.models = new ModelParserService();
        command.naming = new NamingService();
        command.buildService = new BuildService();
        command.liquibase = new LiquibaseChangelogService();
        command.github = new GitHubActionsService();
        command.gitHubContextFactory = new GitHubContextFactory();
        command.projectStructureService = new ProjectStructureService();
        command.entityGenerationService = entityGenerationService(templates);
        return command;
    }

    private EntityGenerationService entityGenerationService(TemplateService templates) throws Exception {
        NamingService naming = new NamingService();
        ModelParserService models = new ModelParserService();
        EntityContextFactory contextFactory = new EntityContextFactory();
        configure(contextFactory, "naming", naming);
        configure(contextFactory, "models", models);

        EntityTemplateWriter writer = new EntityTemplateWriter();
        configure(writer, "templates", templates);

        ParentMapperSyncService parentMapper = new ParentMapperSyncService();
        configure(parentMapper, "naming", naming);
        configure(parentMapper, "models", models);

        EntityGenerationService generation = new EntityGenerationService();
        configure(generation, "openApi", new OpenApiService(naming));
        configure(generation, "contextFactory", contextFactory);
        configure(generation, "templateWriter", writer);
        configure(generation, "parentMapperSyncService", parentMapper);
        configure(generation, "generationLogService", new GenerationLogService());
        return generation;
    }

    private void assertGeneratedProjectIsConsistent(Path project, String entity) throws Exception {
        Path internalSpec = project.resolve("src/main/openapi/orders-svc-internal.yaml");
        Path externalSpec = project.resolve("src/main/openapi/orders-svc-external-v1.yaml");
        assertTrue(Files.exists(internalSpec));
        assertTrue(Files.exists(externalSpec));
        String internalOpenApi = Files.readString(internalSpec);
        String externalOpenApi = Files.readString(externalSpec);
        assertTrue(internalOpenApi.contains("title: orders-svc API"));
        assertTrue(internalOpenApi.contains("http://orders-svc:8080/api"));
        assertTrue(internalOpenApi.contains(entity));
        assertTrue(externalOpenApi.contains(entity));
        assertTrue(internalOpenApi.contains("/" + new NamingService().pluralPath(entity)));
        assertTrue(Files.readString(internalSpec).contains("ocx-orders:read"));

        Path javaEntity = project.resolve(
                "src/main/java/com/customer/orders/domain/models/" + entity + ".java"
        );
        assertTrue(Files.exists(javaEntity));
        assertTrue(Files.readString(javaEntity).startsWith("package com.customer.orders.domain.models;"));

        String metadata = Files.readString(project.resolve("src/main/resources/application.properties"));
        assertTrue(metadata.contains("onecx.generator.name=orders-svc"));
        assertTrue(metadata.contains("onecx.generator.group=com.customer"));
        assertTrue(metadata.contains("onecx.generator.package=com.customer.orders"));
        assertTrue(metadata.contains("onecx.generator.artifact-id=orders-svc"));
        assertTrue(metadata.contains("postgresdb:5432/orders_svc"));
        assertTrue(metadata.contains("quarkus.liquibase.migrate-at-start=true"));

        String pom = Files.readString(project.resolve("pom.xml"));
        assertTrue(pom.contains("<groupId>com.customer</groupId>"));
        assertTrue(pom.contains("<artifactId>orders-svc</artifactId>"));
        assertTrue(pom.contains("src/main/openapi/orders-svc-internal.yaml"));
        assertTrue(pom.contains("src/main/openapi/orders-svc-external-v1.yaml"));

        String chart = Files.readString(project.resolve("src/main/helm/Chart.yaml"));
        String values = Files.readString(project.resolve("src/main/helm/values.yaml"));
        assertTrue(chart.contains("name: orders-svc"));
        assertTrue(values.contains("name: orders-svc"));
        assertTrue(values.contains("repository: \"onecx/orders-svc\""));

        Path changelogDir = project.resolve("src/main/resources/db/changelog");
        try (var changelogs = Files.list(changelogDir)) {
            assertTrue(changelogs.anyMatch(path ->
                    path.getFileName().toString().contains("add-" + entity.toLowerCase())));
        }
        assertTrue(Files.readString(project.resolve("src/main/resources/db/changeLog.xml"))
                .contains("db/changelog/"));
    }

    private void configure(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class StubLatestVersionResolver extends LatestVersionResolver {
        @Override
        public ResolvedVersion resolveLatestWithSource(String ownerAndRepo, String fallback) {
            return new ResolvedVersion(fallback, Source.DEFAULT);
        }
    }
}
