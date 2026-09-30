package org.tkit.onecx.onecxsvcgen.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.tkit.onecx.onecxsvcgen.model.EntityDef;

import java.nio.file.Path;
import java.util.Map;

@ApplicationScoped
public class EntityGenerationService {

    @Inject
    OpenApiService openApi;

    @Inject
    EntityContextFactory contextFactory;

    @Inject
    EntityTemplateWriter templateWriter;

    @Inject
    ParentMapperSyncService parentMapperSyncService;

    @Inject
    GenerationLogService generationLogService;

    public void generateEntity(Path projectPath,
                               String pkg,
                               String projectName,
                               String scopePrefix,
                               Path internalSpec,
                               Path externalSpec,
                               EntityDef entityDef,
                               Path templateDir) throws Exception {
        generationLogService.logEntityInput(entityDef);

        openApi.addOrUpdateEntity(
                internalSpec,
                externalSpec,
                scopePrefix,
                entityDef.name(),
                entityDef.fields(),
                entityDef.relations(),
                entityDef.api()
        );

        Map<String, Object> ctx = contextFactory.buildContext(projectName, pkg, scopePrefix, entityDef);
        Path base = projectPath.resolve("src/main/java/" + pkg.replace('.', '/'));
        Path testBase = projectPath.resolve("src/test/java/" + pkg.replace('.', '/'));

        templateWriter.writeEntityFiles(base, testBase, entityDef.name(), entityDef.aggregateRoot(), ctx, templateDir);
        parentMapperSyncService.syncParentMappers(projectPath, pkg, entityDef.api());
        generationLogService.logEntityResult(entityDef);
    }
}

