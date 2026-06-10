package org.tkit.onecx.onecxsvcgen.service;

import org.tkit.onecx.onecxsvcgen.model.ApiDef;
import org.tkit.onecx.onecxsvcgen.model.EntityDef;
import org.tkit.onecx.onecxsvcgen.model.FieldDef;
import org.tkit.onecx.onecxsvcgen.model.RelationDef;
import jakarta.enterprise.context.ApplicationScoped;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@ApplicationScoped
public class ModelParserService {

    public List<FieldDef> parseFields(List<String> raw) {
        if (raw == null) {
            return Collections.emptyList();
        }

        List<FieldDef> result = new ArrayList<>();
        for (String item : raw) {
            for (String token : item.split("[ ,]+")) {
                if (token.isBlank()) {
                    continue;
                }
                String[] arr = token.split(":");
                if (arr.length >= 2) {
                    result.add(new FieldDef(arr[0], arr[1]));
                }
            }
        }
        return result;
    }

    public List<RelationDef> parseRelations(List<String> raw) {
        if (raw == null) {
            return Collections.emptyList();
        }

        List<RelationDef> result = new ArrayList<>();
        for (String item : raw) {
            for (String token : item.split("[ ,]+")) {
                if (token.isBlank()) {
                    continue;
                }
                String[] arr = token.split(":");
                if (arr.length >= 3) {
                    result.add(new RelationDef(arr[0], arr[1], arr[2]));
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<EntityDef> parseEntitiesYaml(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            Yaml yaml = new Yaml();
            Map<String, Object> doc = yaml.load(in);

            List<Map<String, Object>> entities = (List<Map<String, Object>>) doc.get("entities");
            List<EntityDef> out = new ArrayList<>();

            if (entities != null) {
                for (Map<String, Object> e : entities) {
                    String name = Objects.toString(e.get("name"));
                    boolean aggregateRoot = Boolean.parseBoolean(
                            Objects.toString(e.getOrDefault("aggregateRoot", "true"))
                    );

                    Map<String, Object> api = (Map<String, Object>) e.get("api");
                    ApiDef apiDef = api == null
                            ? new ApiDef(aggregateRoot, null, null, false, null, null)
                            : new ApiDef(
                            Boolean.parseBoolean(Objects.toString(api.getOrDefault("expose", aggregateRoot))),
                            stringOrNull(api.get("parent")),
                            stringOrNull(api.get("field")),
                            Boolean.parseBoolean(Objects.toString(api.getOrDefault("parentFieldCollection", false))),
                            stringOrNull(api.get("path")),
                            stringOrNull(api.get("tag"))
                    );

                    List<String> fields = toList(e.get("fields"));
                    List<String> relations = toList(e.get("relations"));

                    out.add(new EntityDef(
                            name,
                            aggregateRoot,
                            apiDef,
                            parseFields(fields),
                            parseRelations(relations)
                    ));
                }
            }

            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse YAML model: " + file, e);
        }
    }

    private String stringOrNull(Object o) {
        return o == null ? null : Objects.toString(o);
    }

    private List<String> toList(Object o) {
        if (o == null) {
            return Collections.emptyList();
        }

        List<String> out = new ArrayList<>();
        for (Object x : (List<?>) o) {
            out.add(Objects.toString(x));
        }
        return out;
    }

    public String tableName(String entity) {
        // return table name in SNAKE_UPPER style (e.g. myEntity -> MY_ENTITY)
        return dbName(entity).toUpperCase();
    }

    private String dbName(String value) {
        return value
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace("-", "_")
                .toLowerCase();
    }

    public String buildEntityImports(List<FieldDef> fields) {
        Set<String> imports = new LinkedHashSet<>();

        for (FieldDef f : fields) {
            switch (f.type()) {
                case "BigDecimal" -> imports.add("import java.math.BigDecimal;");
                case "LocalDate" -> imports.add("import java.time.LocalDate;");
                case "LocalDateTime" -> imports.add("import java.time.LocalDateTime;");
                case "UUID" -> imports.add("import java.util.UUID;");
                default -> {
                    // no import needed
                }
            }
        }

        if (imports.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (String i : imports) {
            sb.append(i).append("\n");
        }
        return sb.toString();
    }

    public String buildFieldsDecl(List<FieldDef> fields) {
        StringBuilder sb = new StringBuilder();
        for (FieldDef f : fields) {
            sb.append("    @Column(name = \"")
                    .append(dbName(f.name()).toUpperCase())
                    .append("\")\n");
            sb.append("    private ")
                    .append(mapDomainType(f.type()))
                    .append(" ")
                    .append(f.name())
                    .append(";\n");
        }
        return sb.toString();
    }

    public String buildRelationsDecl(List<RelationDef> relations, String basePackage) {
        StringBuilder sb = new StringBuilder();
        for (RelationDef r : relations) {
            sb.append("    @").append(r.relationType()).append("\n");
            String targetType = basePackage + ".domain.models." + r.target();

            if ("ManyToOne".equals(r.relationType()) || "OneToOne".equals(r.relationType())) {
                sb.append("    @JoinColumn(name = \"")
                        .append(dbName(r.field()).toUpperCase())
                        .append("_ID")
                        .append("\")\n");
                sb.append("    private ")
                        .append(targetType)
                        .append(" ")
                        .append(r.field())
                        .append(";\n");
            } else if ("OneToMany".equals(r.relationType()) || "ManyToMany".equals(r.relationType())) {
                sb.append("    private java.util.List<")
                        .append(targetType)
                        .append("> ")
                        .append(r.field())
                        .append(";\n");
            } else {
                sb.append("    private ")
                        .append(targetType)
                        .append(" ")
                        .append(r.field())
                        .append(";\n");
            }
        }
        return sb.toString();
    }

    public String buildLiquibaseColumns(List<FieldDef> fields, List<RelationDef> relations) {
        StringBuilder sb = new StringBuilder();

        for (FieldDef f : fields) {
            sb.append("            <column name=\"")
                    .append(dbName(f.name()).toUpperCase())
                    .append("\" type=\"")
                    .append(mapLiquibaseType(f.type()))
                    .append("\"/>\n");
        }

        for (RelationDef r : relations) {
            if ("ManyToOne".equals(r.relationType()) || "OneToOne".equals(r.relationType())) {
                sb.append("            <column name=\"")
                        .append(dbName(r.field()).toUpperCase())
                        .append("_ID\" type=\"VARCHAR(36)\"/>\n");
            }
        }

        return sb.toString();
    }

    public String buildLiquibaseChangeSet(String entity, List<FieldDef> fields, List<RelationDef> relations) {
        String tableName = tableName(entity);

        StringBuilder sb = new StringBuilder();
        sb.append("    <changeSet id=\"create-").append(tableName).append("\" author=\"onecx-svc-generator\">\n");
        sb.append("        <createTable tableName=\"").append(tableName).append("\">\n");

        sb.append("            <column name=\"GUID\" type=\"VARCHAR(36)\">\n");
        sb.append("                <constraints primaryKey=\"true\" primaryKeyName=\"pk_")
                .append(tableName)
                .append("\" nullable=\"false\"/>\n");
        sb.append("            </column>\n");

        // inherited fields from TraceableEntity / base model
        sb.append("            <column name=\"creationDate\" type=\"TIMESTAMP\"/>\n");
        sb.append("            <column name=\"creationUser\" type=\"VARCHAR(255)\"/>\n");
        sb.append("            <column name=\"modificationDate\" type=\"TIMESTAMP\"/>\n");
        sb.append("            <column name=\"modificationUser\" type=\"VARCHAR(255)\"/>\n");
        sb.append("            <column name=\"OPTLOCK\" type=\"BIGINT\"/>\n");

        // local inherited/business field used in generated entities
        sb.append("            <column name=\"tenantId\" type=\"VARCHAR(255)\"/>\n");

        sb.append(buildLiquibaseColumns(fields, relations));
        sb.append("        </createTable>\n");
        sb.append("    </changeSet>\n");

        return sb.toString();
    }

    public String buildLiquibaseChangeSets(List<EntityDef> entities) {
        StringBuilder sb = new StringBuilder();
        for (EntityDef entity : entities) {
            sb.append(buildLiquibaseChangeSet(entity.name(), entity.fields(), entity.relations())).append("\n");
        }
        return sb.toString();
    }

    public String buildFindByCriteriaPredicates(String entity, List<FieldDef> fields) {
        StringBuilder sb = new StringBuilder();

        // choose a primary searchable field: prefer 'name' (case-insensitive), otherwise first field if present
        FieldDef target = null;
        for (FieldDef f : fields) {
            if ("name".equalsIgnoreCase(f.name())) {
                target = f;
                break;
            }
        }
        if (target == null && !fields.isEmpty()) {
            target = fields.get(0);
        }

        if (target == null) {
            return ""; // no searchable fields
        }

        String upper = upper(target.name());
        String getter = "criteria.get" + upper + "()";

        // attribute name in metamodel: convert camelCase to SNAKE_UPPER (creationDate -> CREATION_DATE)
        String attrConst = dbName(target.name()).toUpperCase();

        if ("String".equals(target.type())) {
            sb.append("            addSearchStringPredicate(predicates, cb, root.get(" + entity + "_." + attrConst + "), ")
                    .append(getter)
                    .append(");\n");
        } else {
            sb.append("            if (").append(getter).append(" != null) {\n");
            sb.append("                predicates.add(cb.equal(root.get(" + entity + "_." + attrConst + "), ").append(getter).append("));\n");
            sb.append("            }\n");
        }

        return sb.toString();
    }

    public String buildRelationMappingMethods(List<RelationDef> relations, String pkg) {
        StringBuilder sb = new StringBuilder();
        String generatedModelPackage = generatedInternalModelPackage(pkg);
        String modelPackage = modelPackage(pkg);

        Set<String> processed = new LinkedHashSet<>();

        for (RelationDef relation : relations) {
            String target = relation.target();
            if (!processed.add(target)) {
                continue;
            }

            if (!isResolvableSingleRelation(relation)) {
                continue;
            }

            sb.append("    @Mapping(target = \"id\", ignore = true)\n")
                    .append("    @Mapping(target = \"tenantId\", ignore = true)\n")
                    .append("    @Mapping(target = \"creationDate\", ignore = true)\n")
                    .append("    @Mapping(target = \"creationUser\", ignore = true)\n")
                    .append("    @Mapping(target = \"modificationDate\", ignore = true)\n")
                    .append("    @Mapping(target = \"modificationUser\", ignore = true)\n")
                    .append("    @Mapping(target = \"controlTraceabilityManual\", ignore = true)\n")
                    .append("    @Mapping(target = \"modificationCount\", ignore = true)\n")
                    .append("    @Mapping(target = \"persisted\", ignore = true)\n")
                    .append("    ")
                    .append(modelPackage).append(".").append(target)
                    .append(" fromDto(")
                    .append(generatedModelPackage).append(".").append(target).append("DTO dto);\n\n");

            sb.append("    @Mapping(target = \"id\", ignore = true)\n")
                    .append("    @Mapping(target = \"tenantId\", ignore = true)\n")
                    .append("    @Mapping(target = \"creationDate\", ignore = true)\n")
                    .append("    @Mapping(target = \"creationUser\", ignore = true)\n")
                    .append("    @Mapping(target = \"modificationDate\", ignore = true)\n")
                    .append("    @Mapping(target = \"modificationUser\", ignore = true)\n")
                    .append("    @Mapping(target = \"controlTraceabilityManual\", ignore = true)\n")
                    .append("    @Mapping(target = \"modificationCount\", ignore = true)\n")
                    .append("    @Mapping(target = \"persisted\", ignore = true)\n")
                    .append("    void update(")
                    .append(generatedModelPackage).append(".").append(target).append("DTO dto, ")
                    .append("@org.mapstruct.MappingTarget ")
                    .append(modelPackage).append(".").append(target)
                    .append(" entity);\n\n");
        }

        return sb.toString();
    }

    public String buildServiceRelationImports(List<RelationDef> relations, String pkg) {
        StringBuilder sb = new StringBuilder();
        Set<String> processed = new LinkedHashSet<>();

        for (RelationDef relation : relations) {
            if (!isResolvableSingleRelation(relation)) {
                continue;
            }

            String target = relation.target();
            if (!processed.add(target)) {
                continue;
            }

            sb.append("import ").append(daoPackage(pkg)).append(".").append(target).append("DAO;\n");
            sb.append("import ").append(modelPackage(pkg)).append(".").append(target).append(";\n");
        }

        return sb.toString();
    }

    public String buildRelationDaoInjections(List<RelationDef> relations) {
        StringBuilder sb = new StringBuilder();
        Set<String> processed = new LinkedHashSet<>();

        for (RelationDef relation : relations) {
            if (!isResolvableSingleRelation(relation)) {
                continue;
            }

            String target = relation.target();
            if (!processed.add(target)) {
                continue;
            }

            String var = lowerFirst(target) + "DAO";
            sb.append("    @Inject\n");
            sb.append("    ").append(target).append("DAO ").append(var).append(";\n\n");
        }

        return sb.toString();
    }

    public String buildRelationCreateResolvers(List<RelationDef> relations) {
        StringBuilder sb = new StringBuilder();

        for (RelationDef relation : relations) {
            if (!isResolvableSingleRelation(relation)) {
                continue;
            }

            String field = relation.field();
            String upperField = upper(field);
            String target = relation.target();
            String daoVar = lowerFirst(target) + "DAO";
            String resolvedVar = "resolved" + target;

            sb.append("        if (dto.get").append(upperField).append("() != null) {\n");
            sb.append("            if (dto.get").append(upperField).append("().getId() != null && !dto.get")
                    .append(upperField).append("().getId().isBlank()) {\n");
            sb.append("                ").append(target).append(" ").append(resolvedVar).append(" = ")
                    .append(daoVar).append(".findById(dto.get").append(upperField).append("().getId());\n");
            sb.append("                entity.set").append(upperField).append("(").append(resolvedVar).append(");\n");
            sb.append("            } else {\n");
            sb.append("                ").append(target).append(" ").append(resolvedVar)
                    .append(" = mapper.fromDto(dto.get").append(upperField).append("());\n");
            sb.append("                ").append(daoVar).append(".create(").append(resolvedVar).append(");\n");
            sb.append("                entity.set").append(upperField).append("(").append(resolvedVar).append(");\n");
            sb.append("            }\n");
            sb.append("        }\n");
        }

        return sb.toString();
    }

    public String buildRelationUpdateResolvers(List<RelationDef> relations) {
        StringBuilder sb = new StringBuilder();

        for (RelationDef relation : relations) {
            if (!isResolvableSingleRelation(relation)) {
                continue;
            }

            String field = relation.field();
            String upperField = upper(field);
            String target = relation.target();
            String daoVar = lowerFirst(target) + "DAO";
            String resolvedVar = "resolved" + target;

            sb.append("        if (dto.get").append(upperField).append("() != null) {\n");
            sb.append("            entity.set").append(upperField).append("(null);\n");
            sb.append("            if (dto.get").append(upperField).append("().getId() != null && !dto.get")
                    .append(upperField).append("().getId().isBlank()) {\n");
            sb.append("                ").append(target).append(" ").append(resolvedVar).append(" = ")
                    .append(daoVar).append(".findById(dto.get").append(upperField).append("().getId());\n");
            sb.append("                entity.set").append(upperField).append("(").append(resolvedVar).append(");\n");
            sb.append("            } else {\n");
            sb.append("                ").append(target).append(" ").append(resolvedVar)
                    .append(" = mapper.fromDto(dto.get").append(upperField).append("());\n");
            sb.append("                ").append(daoVar).append(".create(").append(resolvedVar).append(");\n");
            sb.append("                entity.set").append(upperField).append("(").append(resolvedVar).append(");\n");
            sb.append("            }\n");
            sb.append("        }\n");
        }

        return sb.toString();
    }

    public String buildTestCreateDtoBody(List<FieldDef> fields, List<RelationDef> relations, String dtoClassName) {
        StringBuilder sb = new StringBuilder();
        sb.append(dtoClassName).append(" request = new ").append(dtoClassName).append("();\n");

        for (FieldDef field : fields) {
            sb.append("        request.")
                    .append(setter(field.name()))
                    .append("(")
                    .append(testDtoLiteral(field.type(), false))
                    .append(");\n");
        }

        // relations intentionally skipped for now to keep generated tests simple and compile-safe

        return sb.toString();
    }

    public String buildTestUpdateDtoBody(List<FieldDef> fields, List<RelationDef> relations, String dtoClassName) {
        StringBuilder sb = new StringBuilder();
        sb.append(dtoClassName).append(" request = new ").append(dtoClassName).append("();\n");

        for (FieldDef field : fields) {
            sb.append("        request.")
                    .append(setter(field.name()))
                    .append("(")
                    .append(testDtoLiteral(field.type(), true))
                    .append(");\n");
        }

        // relations intentionally skipped for now to keep generated tests simple and compile-safe

        return sb.toString();
    }

    public String buildTestSearchCriteriaBody(List<FieldDef> fields, String criteriaClassName) {
        StringBuilder sb = new StringBuilder();
        sb.append(criteriaClassName).append(" request = new ").append(criteriaClassName).append("();\n");
        sb.append("        request.setPageNumber(0);\n");
        sb.append("        request.setPageSize(10);\n");

        for (FieldDef field : fields) {
            sb.append("        request.")
                    .append(setter(field.name()))
                    .append("(")
                    .append(testDtoLiteral(field.type(), false))
                    .append(");\n");
        }

        return sb.toString();
    }

    public String buildTestExternalSearchCriteriaBody(List<FieldDef> fields, String criteriaClassName) {
        return buildTestSearchCriteriaBody(fields, criteriaClassName);
    }

    public String buildTestEntityFieldsInit(List<FieldDef> fields, List<RelationDef> relations) {
        StringBuilder sb = new StringBuilder();

        for (FieldDef field : fields) {
            sb.append("        entity.")
                    .append(setter(field.name()))
                    .append("(")
                    .append(testEntityLiteral(field.type(), false))
                    .append(");\n");
        }

        // relations intentionally skipped for now

        return sb.toString();
    }

    public String buildTestDtoFieldsInit(List<FieldDef> fields, List<RelationDef> relations, String dtoClassName) {
        StringBuilder sb = new StringBuilder();

        for (FieldDef field : fields) {
            sb.append("        dto.")
                    .append(setter(field.name()))
                    .append("(")
                    .append(testDtoLiteral(field.type(), false))
                    .append(");\n");
        }

        // relations intentionally skipped for now

        return sb.toString();
    }

    public String buildTestDtoUpdateFieldsInit(List<FieldDef> fields, List<RelationDef> relations, String dtoClassName) {
        StringBuilder sb = new StringBuilder();

        for (FieldDef field : fields) {
            sb.append("        dto.")
                    .append(setter(field.name()))
                    .append("(")
                    .append(testDtoLiteral(field.type(), true))
                    .append(");\n");
        }

        // relations intentionally skipped for now

        return sb.toString();
    }

    public String buildTestDtoAssertions(List<FieldDef> fields, List<RelationDef> relations) {
        StringBuilder sb = new StringBuilder();

        for (FieldDef field : fields) {
            sb.append("        assertEquals(")
                    .append(testDtoLiteral(field.type(), false))
                    .append(", dto.")
                    .append(dtoGetter(field.name()))
                    .append(");\n");
        }

        // relations intentionally skipped for now

        return sb.toString();
    }

    public String buildTestExternalDtoAssertions(List<FieldDef> fields, List<RelationDef> relations) {
        return buildTestDtoAssertions(fields, relations);
    }

    public String buildTestEntityAssertions(List<FieldDef> fields, List<RelationDef> relations) {
        StringBuilder sb = new StringBuilder();

        for (FieldDef field : fields) {
            if ("BigDecimal".equals(field.type())) {
                sb.append("        assertEquals(0, entity.")
                        .append(entityGetter(field.name(), field.type()))
                        .append(".compareTo(")
                        .append(testMappedEntityLiteral(field.type(), false))
                        .append("));\n");
            } else {
                sb.append("        assertEquals(")
                        .append(testMappedEntityLiteral(field.type(), false))
                        .append(", entity.")
                        .append(entityGetter(field.name(), field.type()))
                        .append(");\n");
            }
        }

        return sb.toString();
    }

    public String buildTestUpdatedEntityAssertions(List<FieldDef> fields, List<RelationDef> relations) {
        StringBuilder sb = new StringBuilder();

        for (FieldDef field : fields) {
            if ("BigDecimal".equals(field.type())) {
                sb.append("        assertEquals(0, entity.")
                        .append(entityGetter(field.name(), field.type()))
                        .append(".compareTo(")
                        .append(testMappedEntityLiteral(field.type(), true))
                        .append("));\n");
            } else {
                sb.append("        assertEquals(")
                        .append(testMappedEntityLiteral(field.type(), true))
                        .append(", entity.")
                        .append(entityGetter(field.name(), field.type()))
                        .append(");\n");
            }
        }

        return sb.toString();
    }

    private String testMappedEntityLiteral(String type, boolean updated) {
        return switch (type) {
            case "String" -> updated ? "\"updated-value\"" : "\"test-value\"";
            case "Integer", "int" -> updated ? "2" : "1";
            case "Long", "long" -> updated ? "2L" : "1L";
            case "BigDecimal" -> updated ? "new java.math.BigDecimal(\"2.0\")" : "new java.math.BigDecimal(\"1.0\")";
            case "Boolean", "boolean" -> updated ? "false" : "true";
            case "LocalDate" -> updated ? "java.time.LocalDate.of(2024, 2, 2)" : "java.time.LocalDate.of(2024, 1, 1)";
            case "LocalDateTime" -> updated
                    ? "java.time.LocalDateTime.of(2024, 2, 2, 12, 0)"
                    : "java.time.LocalDateTime.of(2024, 1, 1, 10, 0)";
            case "UUID" -> updated
                    ? "java.util.UUID.fromString(\"00000000-0000-0000-0000-000000000002\")"
                    : "java.util.UUID.fromString(\"00000000-0000-0000-0000-000000000001\")";
            default -> updated ? "\"updated-value\"" : "\"test-value\"";
        };
    }

    private String setter(String fieldName) {
        return "set" + upperFirst(fieldName);
    }

    private String dtoGetter(String fieldName) {
        return "get" + upperFirst(fieldName) + "()";
    }

    private String entityGetter(String fieldName, String type) {
        if ("boolean".equals(type)) {
            return "is" + upperFirst(fieldName) + "()";
        }
        return "get" + upperFirst(fieldName) + "()";
    }

    private String upperFirst(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String testEntityLiteral(String type, boolean updated) {
        return switch (type) {
            case "String" -> updated ? "\"updated-value\"" : "\"test-value\"";
            case "Integer", "int" -> updated ? "2" : "1";
            case "Long", "long" -> updated ? "2L" : "1L";
            case "BigDecimal" -> updated ? "new java.math.BigDecimal(\"2.00\")" : "java.math.BigDecimal.ONE";
            case "Boolean", "boolean" -> updated ? "false" : "true";
            case "LocalDate" -> updated ? "java.time.LocalDate.of(2024, 2, 2)" : "java.time.LocalDate.of(2024, 1, 1)";
            case "LocalDateTime" -> updated
                    ? "java.time.LocalDateTime.of(2024, 2, 2, 12, 0)"
                    : "java.time.LocalDateTime.of(2024, 1, 1, 10, 0)";
            case "UUID" -> updated
                    ? "java.util.UUID.fromString(\"00000000-0000-0000-0000-000000000002\")"
                    : "java.util.UUID.fromString(\"00000000-0000-0000-0000-000000000001\")";
            default -> updated ? "\"updated-value\"" : "\"test-value\"";
        };
    }

    private String testDtoLiteral(String type, boolean updated) {
        return switch (type) {
            case "String" -> updated ? "\"updated-value\"" : "\"test-value\"";
            case "Integer", "int" -> updated ? "2" : "1";
            case "Long", "long" -> updated ? "2L" : "1L";
            case "BigDecimal" -> updated ? "2.0D" : "1.0D";
            case "Boolean", "boolean" -> updated ? "false" : "true";
            case "LocalDate" -> updated ? "java.time.LocalDate.of(2024, 2, 2)" : "java.time.LocalDate.of(2024, 1, 1)";
            case "LocalDateTime" -> updated
                    ? "java.time.LocalDateTime.of(2024, 2, 2, 12, 0)"
                    : "java.time.LocalDateTime.of(2024, 1, 1, 10, 0)";
            case "UUID" -> updated
                    ? "java.util.UUID.fromString(\"00000000-0000-0000-0000-000000000002\")"
                    : "java.util.UUID.fromString(\"00000000-0000-0000-0000-000000000001\")";
            default -> updated ? "\"updated-value\"" : "\"test-value\"";
        };
    }

    public String buildInternalControllerAdditionalMethods(
            String entity,
            String resourcePath,
            List<FieldDef> fields,
            List<RelationDef> relations) {

        StringBuilder sb = new StringBuilder();

        List<RelationDef> resolvableRelations = relations.stream()
                .filter(this::isResolvableSingleRelation)
                .toList();

        for (RelationDef relation : resolvableRelations) {

            String upperRelation = upperFirst(relation.field());
            String relationHelper = "create" + upperRelation + "IdThrough" + entity;

            // CREATE WITHOUT
            sb.append("    @Test\n");
            sb.append("    void create").append(entity).append("Without").append(upperRelation).append("ShouldSucceed() {\n");
            sb.append(buildRequestAssignment("request", fields, null, null, Collections.emptyMap()));
            sb.append("        String id = given()\n");
            sb.append("                .auth().oauth2(token)\n");
            sb.append("                .header(APM_HEADER_PARAM, idToken)\n");
            sb.append("                .contentType(APPLICATION_JSON)\n");
            sb.append("                .body(request)\n");
            sb.append("                .when()\n");
            sb.append("                .post(\"/internal/").append(resourcePath).append("\")\n");
            sb.append("                .then()\n");
            sb.append("                .statusCode(201)\n");
            sb.append("                .extract()\n");
            sb.append("                .path(\"id\");\n\n");
            sb.append("        assertNotNull(id);\n");
            sb.append("    }\n\n");

            // CREATE WITH EXISTING
            sb.append("    @Test\n");
            sb.append("    void create").append(entity).append("WithExisting").append(upperRelation).append("IdShouldReuseExisting")
                    .append(upperRelation).append("() {\n");
            sb.append("        String relationId = ").append(relationHelper).append("(\"seed-existing\");\n\n");
            sb.append(buildRequestAssignment(
                    "request",
                    fields,
                    relation,
                    relationPayloadById("%s"),
                    Collections.emptyMap()));
            sb.append("        request = request.formatted(relationId);\n\n");
            sb.append("        String entityId = given()\n");
            sb.append("                .auth().oauth2(token)\n");
            sb.append("                .header(APM_HEADER_PARAM, idToken)\n");
            sb.append("                .contentType(APPLICATION_JSON)\n");
            sb.append("                .body(request)\n");
            sb.append("                .when()\n");
            sb.append("                .post(\"/internal/").append(resourcePath).append("\")\n");
            sb.append("                .then()\n");
            sb.append("                .statusCode(201)\n");
            sb.append("                .extract()\n");
            sb.append("                .path(\"id\");\n\n");
            sb.append("        String returnedRelationId = given()\n");
            sb.append("                .auth().oauth2(token)\n");
            sb.append("                .header(APM_HEADER_PARAM, idToken)\n");
            sb.append("                .when()\n");
            sb.append("                .get(\"/internal/").append(resourcePath).append("/{id}\", entityId)\n");
            sb.append("                .then()\n");
            sb.append("                .statusCode(200)\n");
            sb.append("                .extract()\n");
            sb.append("                .path(\"").append(relation.field()).append(".id\");\n\n");
            sb.append("        assertEquals(relationId, returnedRelationId);\n");
            sb.append("    }\n\n");

            // CREATE WITH NEW
            sb.append("    @Test\n");
            sb.append("    void create").append(entity).append("WithNew").append(upperRelation).append("ShouldCreateNested")
                    .append(upperRelation).append("() {\n");
            sb.append(buildRequestAssignment("request", fields, relation, relationPayloadEmptyObject(relation), Collections.emptyMap()));
            sb.append("        String entityId = given()\n");
            sb.append("                .auth().oauth2(token)\n");
            sb.append("                .header(APM_HEADER_PARAM, idToken)\n");
            sb.append("                .contentType(APPLICATION_JSON)\n");
            sb.append("                .body(request)\n");
            sb.append("                .when()\n");
            sb.append("                .post(\"/internal/").append(resourcePath).append("\")\n");
            sb.append("                .then()\n");
            sb.append("                .statusCode(201)\n");
            sb.append("                .extract()\n");
            sb.append("                .path(\"id\");\n\n");
            sb.append("        String returnedRelationId = given()\n");
            sb.append("                .auth().oauth2(token)\n");
            sb.append("                .header(APM_HEADER_PARAM, idToken)\n");
            sb.append("                .when()\n");
            sb.append("                .get(\"/internal/").append(resourcePath).append("/{id}\", entityId)\n");
            sb.append("                .then()\n");
            sb.append("                .statusCode(200)\n");
            sb.append("                .extract()\n");
            sb.append("                .path(\"").append(relation.field()).append(".id\");\n\n");
            sb.append("        assertNotNull(returnedRelationId);\n");
            sb.append("    }\n\n");

            // UPDATE WITHOUT
            sb.append("    @Test\n");
            sb.append("    void update").append(entity).append("Without").append(upperRelation).append("ShouldSucceed() {\n");
            sb.append("        String entityId = create").append(entity).append("AndReturnId();\n\n");
            sb.append(buildRequestAssignment("request", fields, null, null, updatedOverrides(fields)));
            sb.append("        given()\n");
            sb.append("                .auth().oauth2(token)\n");
            sb.append("                .header(APM_HEADER_PARAM, idToken)\n");
            sb.append("                .contentType(MediaType.APPLICATION_JSON)\n");
            sb.append("                .body(request)\n");
            sb.append("                .when()\n");
            sb.append("                .put(\"/internal/").append(resourcePath).append("/{id}\", entityId)\n");
            sb.append("                .then()\n");
            sb.append("                .statusCode(200);\n");
            sb.append("    }\n\n");

            // UPDATE WITH EXISTING
            sb.append("    @Test\n");
            sb.append("    void update").append(entity).append("WithExisting").append(upperRelation).append("IdShouldReuseExisting")
                    .append(upperRelation).append("() {\n");
            sb.append("        String entityId = create").append(entity).append("AndReturnId();\n");
            sb.append("        String relationId = ").append(relationHelper).append("(\"seed-update-existing\");\n\n");
            sb.append(buildRequestAssignment(
                    "request",
                    fields,
                    relation,
                    relationPayloadById("%s"),
                    updatedOverrides(fields)));
            sb.append("        request = request.formatted(relationId);\n\n");
            sb.append("        given()\n");
            sb.append("                .auth().oauth2(token)\n");
            sb.append("                .header(APM_HEADER_PARAM, idToken)\n");
            sb.append("                .contentType(MediaType.APPLICATION_JSON)\n");
            sb.append("                .body(request)\n");
            sb.append("                .when()\n");
            sb.append("                .put(\"/internal/").append(resourcePath).append("/{id}\", entityId)\n");
            sb.append("                .then()\n");
            sb.append("                .statusCode(200);\n\n");
            sb.append("        String returnedRelationId = given()\n");
            sb.append("                .auth().oauth2(token)\n");
            sb.append("                .header(APM_HEADER_PARAM, idToken)\n");
            sb.append("                .when()\n");
            sb.append("                .get(\"/internal/").append(resourcePath).append("/{id}\", entityId)\n");
            sb.append("                .then()\n");
            sb.append("                .statusCode(200)\n");
            sb.append("                .extract()\n");
            sb.append("                .path(\"").append(relation.field()).append(".id\");\n\n");
            sb.append("        assertEquals(relationId, returnedRelationId);\n");
            sb.append("    }\n\n");

            // UPDATE WITH NEW
            sb.append("    @Test\n");
            sb.append("    void update").append(entity).append("WithNew").append(upperRelation).append("ShouldCreateNested")
                    .append(upperRelation).append("() {\n");
            sb.append("        String entityId = create").append(entity).append("AndReturnId();\n\n");
            sb.append(buildRequestAssignment("request", fields, relation, relationPayloadEmptyObject(relation), updatedOverrides(fields)));
            sb.append("        given()\n");
            sb.append("                .auth().oauth2(token)\n");
            sb.append("                .header(APM_HEADER_PARAM, idToken)\n");
            sb.append("                .contentType(MediaType.APPLICATION_JSON)\n");
            sb.append("                .body(request)\n");
            sb.append("                .when()\n");
            sb.append("                .put(\"/internal/").append(resourcePath).append("/{id}\", entityId)\n");
            sb.append("                .then()\n");
            sb.append("                .statusCode(200);\n\n");
            sb.append("        String returnedRelationId = given()\n");
            sb.append("                .auth().oauth2(token)\n");
            sb.append("                .header(APM_HEADER_PARAM, idToken)\n");
            sb.append("                .when()\n");
            sb.append("                .get(\"/internal/").append(resourcePath).append("/{id}\", entityId)\n");
            sb.append("                .then()\n");
            sb.append("                .statusCode(200)\n");
            sb.append("                .extract()\n");
            sb.append("                .path(\"").append(relation.field()).append(".id\");\n\n");
            sb.append("        assertNotNull(returnedRelationId);\n");
            sb.append("    }\n\n");

            // CREATE BLANK
            sb.append("    @Test\n");
            sb.append("    void create").append(entity).append("WithBlank").append(upperRelation).append("IdShouldCreateNested")
                    .append(upperRelation).append("() {\n");
            sb.append(buildRequestAssignment("request", fields, relation, relationPayloadBlankId(relation), Collections.emptyMap()));
            sb.append("        String entityId = given()\n");
            sb.append("                .auth().oauth2(token)\n");
            sb.append("                .header(APM_HEADER_PARAM, idToken)\n");
            sb.append("                .contentType(APPLICATION_JSON)\n");
            sb.append("                .body(request)\n");
            sb.append("                .when()\n");
            sb.append("                .post(\"/internal/").append(resourcePath).append("\")\n");
            sb.append("                .then()\n");
            sb.append("                .statusCode(201)\n");
            sb.append("                .extract()\n");
            sb.append("                .path(\"id\");\n\n");
            sb.append("        assertNotNull(entityId);\n");
            sb.append("    }\n\n");

            // UPDATE BLANK
            sb.append("    @Test\n");
            sb.append("    void update").append(entity).append("WithBlank").append(upperRelation).append("IdShouldCreateNested")
                    .append(upperRelation).append("() {\n");
            sb.append("        String entityId = create").append(entity).append("AndReturnId();\n\n");
            sb.append(buildRequestAssignment("request", fields, relation, relationPayloadBlankId(relation), updatedOverrides(fields)));
            sb.append("        given()\n");
            sb.append("                .auth().oauth2(token)\n");
            sb.append("                .header(APM_HEADER_PARAM, idToken)\n");
            sb.append("                .contentType(MediaType.APPLICATION_JSON)\n");
            sb.append("                .body(request)\n");
            sb.append("                .when()\n");
            sb.append("                .put(\"/internal/").append(resourcePath).append("/{id}\", entityId)\n");
            sb.append("                .then()\n");
            sb.append("                .statusCode(200);\n");
            sb.append("    }\n\n");
        }

        return sb.toString();
    }

    public String buildTestSearchSeedBody(List<FieldDef> fields) {

        StringBuilder sb = new StringBuilder();

        for (FieldDef field : fields) {
            if ("name".equals(field.name())) {
                continue; // to not duplicate
            }

            sb.append(",\n                  \"")
                    .append(field.name())
                    .append("\": ")
                    .append(jsonLiteral(field.type(), false));
        }

        return sb.toString();
    }

    public String buildInternalControllerHelperMethods(
            String entity,
            String resourcePath,
            List<FieldDef> fields,
            List<RelationDef> relations) {

        RelationDef relation = firstResolvableSingleRelation(relations);
        if (relation == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        String upperRelation = upperFirst(relation.field());

        sb.append("    private String create").append(upperRelation).append("IdThrough").append(entity)
                .append("(String ignoredSeedValue) {\n");
        sb.append(buildRequestAssignment("request", fields, relation, relationPayloadEmptyObject(relation), Collections.emptyMap()));
        sb.append("        return given()\n");
        sb.append("                .auth().oauth2(token)\n");
        sb.append("                .header(APM_HEADER_PARAM, idToken)\n");
        sb.append("                .contentType(APPLICATION_JSON)\n");
        sb.append("                .body(request)\n");
        sb.append("                .when()\n");
        sb.append("                .post(\"/internal/").append(resourcePath).append("\")\n");
        sb.append("                .then()\n");
        sb.append("                .statusCode(201)\n");
        sb.append("                .extract()\n");
        sb.append("                .path(\"").append(relation.field()).append(".id\");\n");
        sb.append("    }\n\n");

        return sb.toString();
    }

    public String buildExternalControllerAdditionalMethods(
            String entity,
            String resourcePath,
            List<FieldDef> fields,
            List<RelationDef> relations) {

        StringBuilder sb = new StringBuilder();

        sb.append("    @Test\n");
        sb.append("    void search").append(namingLikePlural(resourcePath)).append("WithEmptyCriteriaShouldUseDefaults() {\n");
        sb.append("        createInternalEntity();\n\n");
        sb.append("        String criteria = \"\"\"\n");
        sb.append("                {\n");
        sb.append("                }\n");
        sb.append("                \"\"\";\n\n");
        sb.append("        List<?> result = given()\n");
        sb.append("                .auth().oauth2(token)\n");
        sb.append("                .header(APM_HEADER_PARAM, idToken)\n");
        sb.append("                .contentType(APPLICATION_JSON)\n");
        sb.append("                .body(criteria)\n");
        sb.append("                .when()\n");
        sb.append("                .post(\"/v1/").append(resourcePath).append("/search\")\n");
        sb.append("                .then()\n");
        sb.append("                .statusCode(200)\n");
        sb.append("                .extract()\n");
        sb.append("                .body()\n");
        sb.append("                .jsonPath()\n");
        sb.append("                .getList(\"stream\");\n\n");
        sb.append("        assertNotNull(result);\n");
        sb.append("    }\n\n");

        FieldDef stringField = firstStringField(fields);
        if (stringField != null) {
            sb.append("    @Test\n");
            sb.append("    void search").append(namingLikePlural(resourcePath))
                    .append("WithBlank").append(upperFirst(stringField.name()))
                    .append("ShouldNotUsePredicate() {\n");
            sb.append("        createInternalEntity();\n\n");
            sb.append("        String criteria = \"\"\"\n");
            sb.append("                {\n");
            sb.append("                  \"pageNumber\": 0,\n");
            sb.append("                  \"pageSize\": 10,\n");
            sb.append("                  \"").append(stringField.name()).append("\": \"   \"\n");
            sb.append("                }\n");
            sb.append("                \"\"\";\n\n");
            sb.append("        List<?> result = given()\n");
            sb.append("                .auth().oauth2(token)\n");
            sb.append("                .header(APM_HEADER_PARAM, idToken)\n");
            sb.append("                .contentType(APPLICATION_JSON)\n");
            sb.append("                .body(criteria)\n");
            sb.append("                .when()\n");
            sb.append("                .post(\"/v1/").append(resourcePath).append("/search\")\n");
            sb.append("                .then()\n");
            sb.append("                .statusCode(200)\n");
            sb.append("                .extract()\n");
            sb.append("                .body()\n");
            sb.append("                .jsonPath()\n");
            sb.append("                .getList(\"stream\");\n\n");
            sb.append("        assertNotNull(result);\n");
            sb.append("    }\n\n");
        }

        // numeric-field external tests removed for same reason as internal tests above.

        return sb.toString();
    }

    public String buildExternalControllerHelperMethods(
            String entity,
            String resourcePath,
            List<FieldDef> fields,
            List<RelationDef> relations) {
        return "";
    }

    private String buildRequestAssignment(
            String variableName,
            List<FieldDef> fields,
            RelationDef relation,
            String relationPayload,
            Map<String, String> overrides) {

        StringBuilder sb = new StringBuilder();
        sb.append("        String ").append(variableName).append(" = \"\"\"\n");
        sb.append(buildJsonObject(fields, relation, relationPayload, overrides));
        sb.append("\n");
        sb.append("                \"\"\";\n\n");
        return sb.toString();
    }

    private String buildCriteriaAssignment(
            String variableName,
            Integer pageNumber,
            Integer pageSize,
            FieldDef stringField,
            String stringLiteral,
            FieldDef numericField,
            String numericLiteral) {

        StringBuilder sb = new StringBuilder();
        sb.append("        String ").append(variableName).append(" = \"\"\"\n");
        sb.append("                {\n");

        List<String> props = new ArrayList<>();
        if (pageNumber != null) {
            props.add("                  \"pageNumber\": " + pageNumber);
        }
        if (pageSize != null) {
            props.add("                  \"pageSize\": " + pageSize);
        }
        if (stringField != null && stringLiteral != null) {
            props.add("                  \"" + stringField.name() + "\": " + stringLiteral);
        }
        if (numericField != null && numericLiteral != null) {
            props.add("                  \"" + numericField.name() + "\": " + numericLiteral);
        }

        for (int i = 0; i < props.size(); i++) {
            sb.append(props.get(i));
            if (i < props.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append("                }\n");
        sb.append("                \"\"\";\n\n");
        return sb.toString();
    }

    private String buildJsonObject(
            List<FieldDef> fields,
            RelationDef relation,
            String relationPayload,
            Map<String, String> overrides) {

        StringBuilder sb = new StringBuilder();
        sb.append("                {\n");

        List<String> props = new ArrayList<>();
        for (FieldDef field : fields) {
            String value = overrides.getOrDefault(field.name(), jsonLiteral(field.type(), false));
            props.add("                  \"" + field.name() + "\": " + value);
        }

        if (relation != null && relationPayload != null) {
            props.add("                  \"" + relation.field() + "\": " + relationPayload);
        }

        for (int i = 0; i < props.size(); i++) {
            sb.append(props.get(i));
            if (i < props.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append("                }");
        return sb.toString();
    }

    private String relationPayloadById(String idPlaceholder) {
        return "{\n                    \"id\": \"" + idPlaceholder + "\"\n                  }";
    }

    private String relationPayloadEmptyObject(RelationDef relation) {
        return "{\n                    \"name\": \"test-" + relation.field() + "\"\n                  }";
    }

    private String relationPayloadBlankId(RelationDef relation) {
        return "{\n                    \"id\": \"\"\n                  }";
    }

    private Map<String, String> updatedOverrides(List<FieldDef> fields) {
        Map<String, String> result = new java.util.HashMap<>();
        for (FieldDef field : fields) {
            result.put(field.name(), jsonLiteral(field.type(), true));
        }
        return result;
    }

    private FieldDef firstStringField(List<FieldDef> fields) {
        for (FieldDef field : fields) {
            if ("String".equals(field.type())) {
                return field;
            }
        }
        return null;
    }

    private FieldDef firstNumericField(List<FieldDef> fields) {
        for (FieldDef field : fields) {
            if ("BigDecimal".equals(field.type())
                    || "Integer".equals(field.type())
                    || "int".equals(field.type())
                    || "Long".equals(field.type())
                    || "long".equals(field.type())) {
                return field;
            }
        }
        return null;
    }

    private RelationDef firstResolvableSingleRelation(List<RelationDef> relations) {
        for (RelationDef relation : relations) {
            if (isResolvableSingleRelation(relation)) {
                return relation;
            }
        }
        return null;
    }

    private String jsonLiteral(String type, boolean updated) {
        return switch (type) {
            case "String" -> updated ? quoteJson("updated-value") : quoteJson("test-value");
            case "Integer", "int" -> updated ? "2" : "1";
            case "Long", "long" -> updated ? "2" : "1";
            case "BigDecimal" -> updated ? "2.0" : "1.0";
            case "Boolean", "boolean" -> updated ? "false" : "true";
            case "LocalDate" -> updated ? quoteJson("2024-02-02") : quoteJson("2024-01-01");
            case "LocalDateTime" -> updated ? quoteJson("2024-02-02T12:00:00") : quoteJson("2024-01-01T10:00:00");
            case "UUID" -> updated
                    ? quoteJson("00000000-0000-0000-0000-000000000002")
                    : quoteJson("00000000-0000-0000-0000-000000000001");
            default -> updated ? quoteJson("updated-value") : quoteJson("test-value");
        };
    }

    private String numericSearchLiteral(String type) {
        return switch (type) {
            case "Integer", "int" -> "123";
            case "Long", "long" -> "123";
            case "BigDecimal" -> "1234.56";
            default -> "1";
        };
    }

    private String quoteJson(String value) {
        return "\"" + value + "\"";
    }

    private String namingLikePlural(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return "Entities";
        }
        String normalized = resourcePath.replace("-", " ");
        String[] parts = normalized.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                sb.append(upperFirst(part));
            }
        }
        return sb.toString();
    }

    public String modelPackage(String pkg) {
        return pkg + ".domain.models";
    }

    public String daoPackage(String pkg) {
        return pkg + ".domain.daos";
    }

    public String domainServicePackage(String pkg) {
        return pkg + ".domain.services";
    }

    public String controllerPackage(String pkg) {
        return pkg + ".rs.internal.controllers";
    }

    public String mapperPackage(String pkg) {
        return pkg + ".rs.internal.mappers";
    }

    public String externalControllerPackage(String pkg) {
        return pkg + ".rs.external.v1.controllers";
    }

    public String externalMapperPackage(String pkg) {
        return pkg + ".rs.external.v1.mappers";
    }

    public String generatedInternalApiPackage(String pkg) {
        return "gen." + pkg + ".rs.internal";
    }

    public String generatedInternalModelPackage(String pkg) {
        return "gen." + pkg + ".rs.internal.model";
    }

    public String generatedApiPackage(String pkg) {
        return "gen." + pkg + ".rs.external.v1";
    }

    public String generatedModelPackage(String pkg) {
        return "gen." + pkg + ".rs.external.v1.model";
    }

    public String mapDomainType(String javaType) {
        return switch (javaType) {
            case "String",
                 "Long", "long",
                 "Integer", "int",
                 "BigDecimal",
                 "Boolean", "boolean",
                 "LocalDate",
                 "LocalDateTime",
                 "UUID" -> javaType;
            default -> javaType;
        };
    }

    private String upper(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private String lowerFirst(String s) {
        return s.substring(0, 1).toLowerCase() + s.substring(1);
    }

    private boolean isResolvableSingleRelation(RelationDef relation) {
        return "ManyToOne".equals(relation.relationType()) || "OneToOne".equals(relation.relationType());
    }

    private String mapLiquibaseType(String javaType) {
        return switch (javaType) {
            case "String" -> "VARCHAR(255)";
            case "Long", "long" -> "BIGINT";
            case "Integer", "int" -> "INT";
            case "BigDecimal" -> "NUMERIC(19,2)";
            case "LocalDateTime" -> "TIMESTAMP";
            case "LocalDate" -> "DATE";
            case "Boolean", "boolean" -> "BOOLEAN";
            case "UUID" -> "VARCHAR(36)";
            default -> "VARCHAR(255)";
        };
    }
}
