package org.tkit.onecx.onecxsvcgen.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

@ApplicationScoped
public class LiquibaseChangelogService {

    public Path ensureStructure(Path projectPath) {
        try {
            Path dbDir = projectPath.resolve("src/main/resources/db");
            Path changelogDir = dbDir.resolve("changelog");
            Files.createDirectories(changelogDir);

            Path root = dbDir.resolve("changeLog.xml");
            if (!Files.exists(root)) {
                String content = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <databaseChangeLog
                                xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                xsi:schemaLocation="
                                   http://www.liquibase.org/xml/ns/dbchangelog
                                   https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

                        </databaseChangeLog>
                        """;
                Files.writeString(root, content);
            }

            return changelogDir;
        } catch (Exception e) {
            throw new RuntimeException("Failed to prepare Liquibase structure", e);
        }
    }

    public String initialFileName() {
        return LocalDate.now() + "-init.xml";
    }

    public String batchFileName() {
        return LocalDate.now() + "-batch-model.xml";
    }

    public String entityFileName(String entity) {
        return LocalDate.now() + "-add-" + normalize(entity) + ".xml";
    }

    public boolean hasIncludedChangelog(Path projectPath) {
        try {
            Path root = projectPath.resolve("src/main/resources/db/changeLog.xml");
            if (!Files.exists(root)) {
                return false;
            }
            String content = Files.readString(root);
            return content.contains("<include ");
        } catch (Exception e) {
            throw new RuntimeException("Failed to inspect root Liquibase changelog", e);
        }
    }

    public void registerInclude(Path projectPath, String fileName) {
        try {
            Path root = projectPath.resolve("src/main/resources/db/changeLog.xml");
            ensureStructure(projectPath);

            String includeLine = "    <include file=\"db/changelog/" + fileName + "\" relativeToChangelogFile=\"false\"/>\n";
            String content = Files.readString(root);

            if (content.contains("db/changelog/" + fileName)) {
                return;
            }

            String marker = "</databaseChangeLog>";
            int idx = content.lastIndexOf(marker);
            if (idx < 0) {
                throw new IllegalStateException("Invalid root Liquibase changelog: missing </databaseChangeLog>");
            }

            String updated = content.substring(0, idx) + includeLine + content.substring(idx);
            Files.writeString(root, updated);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register Liquibase include for " + fileName, e);
        }
    }

    public void importDiffResult(Path projectPath, String fileName) {
        try {
            Path source = projectPath.resolve("target/liquibase-diff-changeLog.xml");
            if (!Files.exists(source)) {
                throw new IllegalStateException("Liquibase diff result not found: " + source);
            }

            Path changelogDir = ensureStructure(projectPath);
            Path target = changelogDir.resolve(fileName);

            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            registerInclude(projectPath, fileName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to import Liquibase diff result", e);
        }
    }

    private String normalize(String value) {
        return value
                .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replace("_", "-")
                .toLowerCase();
    }
}