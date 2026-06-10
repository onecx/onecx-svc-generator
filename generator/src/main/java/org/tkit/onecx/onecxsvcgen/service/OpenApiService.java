package org.tkit.onecx.onecxsvcgen.service;

import org.tkit.onecx.onecxsvcgen.model.ApiDef;
import org.tkit.onecx.onecxsvcgen.model.FieldDef;
import org.tkit.onecx.onecxsvcgen.model.RelationDef;
import jakarta.enterprise.context.ApplicationScoped;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class OpenApiService {

    private static final String PROBLEM_REF = "#/components/schemas/ProblemDetailResponse";

    private final NamingService naming;

    public OpenApiService(NamingService naming) {
        this.naming = naming;
    }

    @SuppressWarnings("unchecked")
    public void addOrUpdateEntity(Path internalOpenApiFile,
                                  Path externalOpenApiFile,
                                  String scopePrefix,
                                  String entity,
                                  List<FieldDef> fields,
                                  List<RelationDef> relations,
                                  ApiDef apiDef) {

        Path internalFile = internalOpenApiFile.toAbsolutePath().normalize();
        Path externalFile = externalOpenApiFile.toAbsolutePath().normalize();

        Map<String, Object> internalSpec = loadYaml(internalFile);
        Map<String, Object> externalSpec = loadYaml(externalFile);

        ensureBase(internalSpec, scopePrefix, internalFile.getFileName().toString().replace(".yaml", ""));
        ensureBase(externalSpec, scopePrefix, externalFile.getFileName().toString().replace(".yaml", ""));

        String resourcePath = apiDef.path() != null ? apiDef.path() : naming.pluralPath(entity);

        String baseTag = apiDef.tag() != null
                ? apiDef.tag()
                : naming.lowerCamel(resourcePath.replace("-", ""));
        String internalTag = baseTag.endsWith("Internal") ? baseTag : baseTag + "Internal";
        String externalTag = baseTag.endsWith("Internal")
                ? baseTag.substring(0, baseTag.length() - "Internal".length())
                : baseTag;

        upsertEntitySchema(internalSpec, entity, fields, relations);
        upsertEntitySchema(externalSpec, entity, fields, relations);

        upsertSearchCriteriaSchema(internalSpec, entity, fields);
        upsertSearchCriteriaSchema(externalSpec, entity, fields);

        upsertPageResultSchema(internalSpec, entity);
        upsertPageResultSchema(externalSpec, entity);

        if (apiDef.expose()) {
            createInternalPaths(internalSpec, resourcePath, internalTag, entity, scopePrefix);
            createExternalPaths(externalSpec, resourcePath, externalTag, entity, scopePrefix);
        } else if (apiDef.parent() != null && apiDef.field() != null) {
            patchParentSchema(internalSpec, apiDef.parent(), apiDef.field(), apiDef.parentFieldCollection(), entity);
            patchParentSchema(externalSpec, apiDef.parent(), apiDef.field(), apiDef.parentFieldCollection(), entity);
        }

        saveYaml(internalFile, internalSpec);
        saveYaml(externalFile, externalSpec);
    }

    @SuppressWarnings("unchecked")
    private void upsertEntitySchema(Map<String, Object> spec,
                                    String entity,
                                    List<FieldDef> fields,
                                    List<RelationDef> relations) {
        Map<String, Object> components =
                (Map<String, Object>) spec.computeIfAbsent("components", k -> new LinkedHashMap<>());
        Map<String, Object> schemas =
                (Map<String, Object>) components.computeIfAbsent("schemas", k -> new LinkedHashMap<>());

        schemas.put(entity, createSchema(fields, relations));
    }

    @SuppressWarnings("unchecked")
    private void upsertSearchCriteriaSchema(Map<String, Object> spec,
                                            String entity,
                                            List<FieldDef> fields) {
        Map<String, Object> components =
                (Map<String, Object>) spec.computeIfAbsent("components", k -> new LinkedHashMap<>());
        Map<String, Object> schemas =
                (Map<String, Object>) components.computeIfAbsent("schemas", k -> new LinkedHashMap<>());

        Map<String, Object> schema = createEmptySchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");

        // preserve ordering to match existing OpenAPI source (description/format/default/type order)
        Map<String, Object> pageNumberProps = new LinkedHashMap<>();
        pageNumberProps.put("description", "The number of page.");
        pageNumberProps.put("format", "int32");
        pageNumberProps.put("default", 0);
        pageNumberProps.put("type", "integer");
        properties.put("pageNumber", pageNumberProps);

        Map<String, Object> pageSizeProps = new LinkedHashMap<>();
        pageSizeProps.put("format", "int32");
        pageSizeProps.put("default", 100);
        pageSizeProps.put("description", "The size of page");
        pageSizeProps.put("maximum", 1000);
        pageSizeProps.put("type", "integer");
        properties.put("pageSize", pageSizeProps);

        for (FieldDef field : fields) {
            properties.put(field.name(), createSimpleProperty(field.type()));
        }

        schemas.put(entity + "SearchCriteria", schema);
    }

    @SuppressWarnings("unchecked")
    private void upsertPageResultSchema(Map<String, Object> spec, String entity) {
        Map<String, Object> components =
                (Map<String, Object>) spec.computeIfAbsent("components", k -> new LinkedHashMap<>());
        Map<String, Object> schemas =
                (Map<String, Object>) components.computeIfAbsent("schemas", k -> new LinkedHashMap<>());

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> totalElementsProps = new LinkedHashMap<>();
        totalElementsProps.put("type", "integer");
        totalElementsProps.put("format", "int64");
        properties.put("totalElements", totalElementsProps);

        Map<String, Object> totalPagesProps = new LinkedHashMap<>();
        totalPagesProps.put("type", "integer");
        totalPagesProps.put("format", "int32");
        properties.put("totalPages", totalPagesProps);

        Map<String, Object> numberProps = new LinkedHashMap<>();
        numberProps.put("type", "integer");
        numberProps.put("format", "int32");
        properties.put("number", numberProps);

        Map<String, Object> sizeProps = new LinkedHashMap<>();
        sizeProps.put("type", "integer");
        sizeProps.put("format", "int32");
        properties.put("size", sizeProps);

        Map<String, Object> streamProp = new LinkedHashMap<>();
        streamProp.put("type", "array");
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("$ref", "#/components/schemas/" + entity);
        streamProp.put("items", items);
        properties.put("stream", streamProp);
        schema.put("properties", properties);

        schemas.put(entity + "PageResult", schema);
    }

    @SuppressWarnings("unchecked")
    private void patchParentSchema(Map<String, Object> spec,
                                   String parent,
                                   String field,
                                   boolean collection,
                                   String childSchemaName) {
        Map<String, Object> components =
                (Map<String, Object>) spec.computeIfAbsent("components", k -> new LinkedHashMap<>());
        Map<String, Object> schemas =
                (Map<String, Object>) components.computeIfAbsent("schemas", k -> new LinkedHashMap<>());

        Map<String, Object> parentSchema =
                (Map<String, Object>) schemas.computeIfAbsent(parent, k -> createEmptySchema());
        Map<String, Object> parentProperties =
                (Map<String, Object>) parentSchema.computeIfAbsent("properties", k -> new LinkedHashMap<>());

        if (collection) {
            Map<String, Object> array = new LinkedHashMap<>();
            array.put("type", "array");
            array.put("items", Map.of("$ref", "#/components/schemas/" + childSchemaName));
            parentProperties.put(field, array);
        } else {
            parentProperties.put(field, Map.of("$ref", "#/components/schemas/" + childSchemaName));
        }
    }

    @SuppressWarnings("unchecked")
    private void createInternalPaths(Map<String, Object> spec,
                                     String resourcePath,
                                     String tag,
                                     String entity,
                                     String scopePrefix) {
        Map<String, Object> paths =
                (Map<String, Object>) spec.computeIfAbsent("paths", k -> new LinkedHashMap<>());

        String collectionPath = "/internal/" + resourcePath;
        String itemPath = collectionPath + "/{id}";
        String searchPath = collectionPath + "/search";

        Map<String, Object> collectionOps = new LinkedHashMap<>();
        collectionOps.put("post", createCreateOperation(tag, entity, scopePrefix));
        paths.put(collectionPath, collectionOps);

        Map<String, Object> itemOps = new LinkedHashMap<>();
        itemOps.put("get", createGetOperation(tag, entity, scopePrefix, null));
        itemOps.put("put", createUpdateOperation(tag, entity, scopePrefix));
        itemOps.put("delete", createDeleteOperation(tag, entity, scopePrefix));
        paths.put(itemPath, itemOps);

        Map<String, Object> searchOps = new LinkedHashMap<>();
        searchOps.put("post", createSearchOperation(tag, entity, scopePrefix, null));
        paths.put(searchPath, searchOps);
    }

    @SuppressWarnings("unchecked")
    private void createExternalPaths(Map<String, Object> spec,
                                     String resourcePath,
                                     String tag,
                                     String entity,
                                     String scopePrefix) {
        Map<String, Object> paths =
                (Map<String, Object>) spec.computeIfAbsent("paths", k -> new LinkedHashMap<>());

        String versionSuffix = "v1";
        String itemPath = "/" + versionSuffix + "/" + resourcePath + "/{id}";
        String searchPath = "/" + versionSuffix + "/" + resourcePath + "/search";

        Map<String, Object> itemOps = new LinkedHashMap<>();
        itemOps.put("get", createGetOperation(tag, entity, scopePrefix, versionSuffix.toUpperCase()));
        paths.put(itemPath, itemOps);

        Map<String, Object> searchOps = new LinkedHashMap<>();
        searchOps.put("post", createSearchOperation(tag, entity, scopePrefix, versionSuffix.toUpperCase()));
        paths.put(searchPath, searchOps);
    }

    private Map<String, Object> createGetOperation(String tag, String entity, String scopePrefix, String operationIdSuffix) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("tags", List.of(tag));
        op.put("description", "Get " + entity + " by ID");
        String opId = "get" + entity + "ById";
        if (operationIdSuffix != null) {
            opId = opId + operationIdSuffix;
        }
        op.put("operationId", opId);

        Map<String, Object> pathParam = new LinkedHashMap<>();
        pathParam.put("name", "id");
        pathParam.put("in", "path");
        pathParam.put("required", true);
        pathParam.put("schema", Map.of("type", "string"));
        op.put("parameters", List.of(pathParam));

        Map<String, Object> responses = new LinkedHashMap<>();
        responses.put("200", successObjectResponse(entity + " found", entity));
        responses.put("400", problemResponse("Invalid request"));
        responses.put("404", problemResponse(entity + " not found"));
        op.put("responses", responses);

        op.put("security", createSecurity(scopePrefix, "read"));
        return op;
    }

    private Map<String, Object> createCreateOperation(String tag, String entity, String scopePrefix) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("tags", List.of(tag));
        op.put("description", "Create " + entity);
        op.put("operationId", "create" + entity);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("required", true);
        requestBody.put("content", Map.of(
                "application/json",
                Map.of("schema", Map.of("$ref", "#/components/schemas/" + entity))
        ));
        op.put("requestBody", requestBody);

        Map<String, Object> responses = new LinkedHashMap<>();
        responses.put("201", successObjectResponse(entity + " created", entity));
        responses.put("400", problemResponse("Validation failed"));
        op.put("responses", responses);

        op.put("security", createSecurity(scopePrefix, "write"));
        return op;
    }

    private Map<String, Object> createUpdateOperation(String tag, String entity, String scopePrefix) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("tags", List.of(tag));
        op.put("description", "Update " + entity);
        op.put("operationId", "update" + entity);

        Map<String, Object> pathParam = new LinkedHashMap<>();
        pathParam.put("name", "id");
        pathParam.put("in", "path");
        pathParam.put("required", true);
        pathParam.put("schema", Map.of("type", "string"));
        op.put("parameters", List.of(pathParam));

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("required", true);
        requestBody.put("content", Map.of(
                "application/json",
                Map.of("schema", Map.of("$ref", "#/components/schemas/" + entity))
        ));
        op.put("requestBody", requestBody);

        Map<String, Object> responses = new LinkedHashMap<>();
        responses.put("200", successObjectResponse(entity + " updated", entity));
        responses.put("400", problemResponse("Validation failed"));
        responses.put("404", problemResponse(entity + " not found"));
        op.put("responses", responses);

        op.put("security", createSecurity(scopePrefix, "write"));
        return op;
    }

    private Map<String, Object> createDeleteOperation(String tag, String entity, String scopePrefix) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("tags", List.of(tag));
        op.put("description", "Delete " + entity);
        op.put("operationId", "delete" + entity);

        Map<String, Object> pathParam = new LinkedHashMap<>();
        pathParam.put("name", "id");
        pathParam.put("in", "path");
        pathParam.put("required", true);
        pathParam.put("schema", Map.of("type", "string"));
        op.put("parameters", List.of(pathParam));

        Map<String, Object> responses = new LinkedHashMap<>();
        responses.put("204", Map.of("description", entity + " deleted"));
        responses.put("400", problemResponse("Invalid request"));
        responses.put("404", problemResponse(entity + " not found"));
        op.put("responses", responses);

        op.put("security", createSecurity(scopePrefix, "delete"));
        return op;
    }

    private Map<String, Object> createSearchOperation(String tag, String entity, String scopePrefix, String operationIdSuffix) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("tags", List.of(tag));
        op.put("description", "Search " + naming.pluralPath(entity));
        String opId = "search" + naming.upperFirst(naming.pluralPath(entity).replace("-", ""));
        if (operationIdSuffix != null) {
            opId = opId + operationIdSuffix;
        }
        op.put("operationId", opId);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("required", false);
        requestBody.put("content", Map.of(
                "application/json",
                Map.of("schema", Map.of("$ref", "#/components/schemas/" + entity + "SearchCriteria"))
        ));
        op.put("requestBody", requestBody);

        Map<String, Object> responses = new LinkedHashMap<>();
        responses.put("200", successObjectResponse("Search result for " + naming.pluralPath(entity), entity + "PageResult"));
        responses.put("400", problemResponse("Validation failed"));
        op.put("responses", responses);

        op.put("security", createSecurity(scopePrefix, "read"));
        return op;
    }

    private List<Map<String, Object>> createSecurity(String scopePrefix, String operationScope) {
        Map<String, Object> oauthScopes = new LinkedHashMap<>();
        oauthScopes.put("oauth2", List.of(
                scopePrefix + ":all",
                scopePrefix + ":" + operationScope
        ));

        List<Map<String, Object>> security = new ArrayList<>();
        security.add(oauthScopes);
        return security;
    }

    private Map<String, Object> successObjectResponse(String description, String schemaName) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("description", description);
        response.put("content", Map.of(
                "application/json",
                Map.of("schema", Map.of("$ref", "#/components/schemas/" + schemaName))
        ));
        return response;
    }

    private Map<String, Object> successArrayResponse(String description, String itemSchemaName) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("description", description);
        response.put("content", Map.of(
                "application/json",
                Map.of("schema", Map.of(
                        "type", "array",
                        "items", Map.of("$ref", "#/components/schemas/" + itemSchemaName)
                ))
        ));
        return response;
    }

    private Map<String, Object> problemResponse(String description) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("description", description);
        response.put("content", Map.of(
                "application/json",
                Map.of("schema", Map.of("$ref", PROBLEM_REF))
        ));
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createSchema(List<FieldDef> fields, List<RelationDef> relations) {
        Map<String, Object> schema = createEmptySchema();
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");

        props.put("id", Map.of("type", "string"));

        for (FieldDef field : fields) {
            props.put(field.name(), createSimpleProperty(field.type()));
        }

        for (RelationDef relation : relations) {
            if ("OneToMany".equals(relation.relationType()) || "ManyToMany".equals(relation.relationType())) {
                props.put(relation.field(), Map.of(
                        "type", "array",
                        "items", Map.of("$ref", "#/components/schemas/" + relation.target())
                ));
            } else {
                props.put(relation.field(), Map.of("$ref", "#/components/schemas/" + relation.target()));
            }
        }

        return schema;
    }

    private Map<String, Object> createEmptySchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<String, Object>());
        return schema;
    }

    private Map<String, Object> createSimpleProperty(String type) {
        Map<String, Object> property = new LinkedHashMap<>();
        switch (type) {
            case "String" -> property.put("type", "string");
            case "Integer", "int" -> {
                property.put("type", "integer");
                property.put("format", "int32");
            }
            case "Long", "long" -> {
                property.put("type", "integer");
                property.put("format", "int64");
            }
            case "BigDecimal" -> {
                property.put("type", "number");
                property.put("format", "double");
            }
            case "Boolean", "boolean" -> property.put("type", "boolean");
            case "LocalDate" -> {
                property.put("type", "string");
                property.put("format", "date");
            }
            case "LocalDateTime" -> {
                property.put("type", "string");
                property.put("format", "date-time");
            }
            case "UUID" -> {
                property.put("type", "string");
                property.put("format", "uuid");
            }
            default -> property.put("type", "string");
        }
        return property;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYaml(Path file) {
        try (InputStream in = Files.newInputStream(file.toAbsolutePath().normalize())) {
            Yaml yaml = new Yaml();
            Object obj = yaml.load(in);
            if (obj == null) {
                return new LinkedHashMap<>();
            }
            return (Map<String, Object>) obj;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load OpenAPI file: " + file, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void ensureBase(Map<String, Object> spec, String scopePrefix, String serviceName) {
        spec.putIfAbsent("openapi", "3.0.3");

        spec.computeIfAbsent("info", k -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("title", serviceName);
            info.put("version", "1.0.0");
            return info;
        });

        spec.computeIfAbsent("servers", k -> {
            List<Map<String, Object>> servers = new ArrayList<>();
            Map<String, Object> server = new LinkedHashMap<>();
            server.put("url", "/api");
            servers.add(server);
            return servers;
        });

        Map<String, Object> components =
                (Map<String, Object>) spec.computeIfAbsent("components", k -> new LinkedHashMap<>());
        Map<String, Object> securitySchemes =
                (Map<String, Object>) components.computeIfAbsent("securitySchemes", k -> new LinkedHashMap<>());

        if (!securitySchemes.containsKey("oauth2")) {
            Map<String, Object> oauth2 = new LinkedHashMap<>();
            oauth2.put("type", "oauth2");

            Map<String, Object> flows = new LinkedHashMap<>();
            Map<String, Object> clientCredentials = new LinkedHashMap<>();
            clientCredentials.put("tokenUrl", "https://oauth.simple.api/token");

            Map<String, Object> scopes = new LinkedHashMap<>();
            scopes.put(scopePrefix + ":all", "Grants access to all operations");
            scopes.put(scopePrefix + ":read", "Grants read access");
            scopes.put(scopePrefix + ":write", "Grants write access");
            scopes.put(scopePrefix + ":delete", "Grants access to delete operations");

            clientCredentials.put("scopes", scopes);
            flows.put("clientCredentials", clientCredentials);
            oauth2.put("flows", flows);

            securitySchemes.put("oauth2", oauth2);
        }

        Map<String, Object> schemas =
                (Map<String, Object>) components.computeIfAbsent("schemas", k -> new LinkedHashMap<>());

        schemas.putIfAbsent("ProblemDetailParam", Map.of(
                "type", "object",
                "properties", Map.of(
                        "key", Map.of("type", "string"),
                        "value", Map.of("type", "string")
                )
        ));

        schemas.putIfAbsent("ProblemDetailInvalidParam", Map.of(
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string"),
                        "message", Map.of("type", "string")
                )
        ));

        schemas.putIfAbsent("ProblemDetailResponse", Map.of(
                "type", "object",
                "properties", Map.of(
                        "errorCode", Map.of("type", "string"),
                        "detail", Map.of("type", "string"),
                        "params", Map.of(
                                "type", "array",
                                "items", Map.of("$ref", "#/components/schemas/ProblemDetailParam")
                        ),
                        "invalidParams", Map.of(
                                "type", "array",
                                "items", Map.of("$ref", "#/components/schemas/ProblemDetailInvalidParam")
                        )
                )
        ));

        spec.computeIfAbsent("paths", k -> new LinkedHashMap<>());
    }

    private void saveYaml(Path file, Map<String, Object> spec) {
        try {
            DumperOptions options = new DumperOptions();
            options.setPrettyFlow(true);
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setIndent(2);
            options.setIndicatorIndent(1);
            options.setWidth(160);

            Yaml yaml = new Yaml(options);

            String dumped = yaml.dump(spec);
            String normalizedYaml = normalizeSecurityScopesInline(dumped);

            Path normalized = file.toAbsolutePath().normalize();
            Files.createDirectories(normalized.getParent());
            Files.writeString(normalized, normalizedYaml);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save OpenAPI file: " + file, e);
        }
    }

    private String normalizeSecurityScopesInline(String yamlText) {
        List<String> lines = yamlText.lines().toList();
        List<String> result = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String current = lines.get(i);

            if (!current.trim().equals("security:")) {
                result.add(current);
                continue;
            }

            result.add(current);

            if (i + 1 >= lines.size()) {
                continue;
            }

            int j = i + 1;

            while (j < lines.size() && lines.get(j).trim().isEmpty()) {
                result.add(lines.get(j));
                j++;
            }

            if (j >= lines.size()) {
                i = j - 1;
                continue;
            }

            String lineAfterSecurity = lines.get(j).trim();

            // wariant:
            // security:
            //   - oauth2:
            if (lineAfterSecurity.equals("- oauth2:")) {
                int oauthIndent = leadingSpaces(lines.get(j));
                List<String> scopes = new ArrayList<>();
                int k = j + 1;

                while (k < lines.size()) {
                    String candidate = lines.get(k);
                    String trimmed = candidate.trim();

                    if (!trimmed.startsWith("- ")) {
                        break;
                    }

                    int candidateIndent = leadingSpaces(candidate);
                    if (candidateIndent <= oauthIndent) {
                        break;
                    }

                    scopes.add(trimmed.substring(2).trim());
                    k++;
                }

                if (!scopes.isEmpty()) {
                    result.add(" ".repeat(oauthIndent) + "- oauth2: [ " + String.join(", ", scopes) + " ]");
                    i = k - 1;
                    continue;
                }
            }

            // wariant:
            // security:
            //   -
            //     oauth2:
            if (lineAfterSecurity.equals("-")) {
                int dashIndent = leadingSpaces(lines.get(j));

                if (j + 1 < lines.size() && lines.get(j + 1).trim().equals("oauth2:")) {
                    int oauthIndent = leadingSpaces(lines.get(j + 1));
                    List<String> scopes = new ArrayList<>();
                    int k = j + 2;

                    while (k < lines.size()) {
                        String candidate = lines.get(k);
                        String trimmed = candidate.trim();

                        if (!trimmed.startsWith("- ")) {
                            break;
                        }

                        int candidateIndent = leadingSpaces(candidate);
                        if (candidateIndent <= oauthIndent) {
                            break;
                        }

                        scopes.add(trimmed.substring(2).trim());
                        k++;
                    }

                    if (!scopes.isEmpty()) {
                        result.add(" ".repeat(dashIndent) + "- oauth2: [ " + String.join(", ", scopes) + " ]");
                        i = k - 1;
                        continue;
                    }
                }
            }
        }

        return String.join("\n", result) + "\n";
    }

    private int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }
}

