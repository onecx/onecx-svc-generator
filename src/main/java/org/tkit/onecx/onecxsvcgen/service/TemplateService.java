package org.tkit.onecx.onecxsvcgen.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@ApplicationScoped
public class TemplateService {

    private final Set<String> usedCustomTemplates = new LinkedHashSet<>();

    private Path configuredTemplateDir;

    private boolean customTemplateDirectoryProvided;


    public void startTemplateSession(Path templateDir) {
        usedCustomTemplates.clear();
        configuredTemplateDir = templateDir;
        customTemplateDirectoryProvided = templateDir != null;
    }

    public void renderToFile(Path templateDir, String resourcePath, Path target, Map<String, ?> ctx) {
        String content = loadTemplate(templateDir, resourcePath);
        for (var e : ctx.entrySet()) {
            content = content.replace("{{" + e.getKey() + "}}", Objects.toString(e.getValue(), ""));
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write file: " + target, e);
        }
    }

    private Path resolveCustomTemplatePath(Path templateDir, String resourcePath) {
        if (templateDir == null || !Files.isDirectory(templateDir)) {
            return null;
        }
        String relativePath = resourcePath;
        if (relativePath.startsWith("templates/")) {
            relativePath = relativePath.substring("templates/".length());
        }
        return templateDir.resolve(relativePath).normalize();
    }

    private String loadCustomTemplate(Path templateDir, String resourcePath) {
        Path customTemplate = resolveCustomTemplatePath(templateDir, resourcePath);
        if (customTemplate == null || !Files.exists(customTemplate)) {
            return null;
        }
        try {
            String displayPath = resourcePath.startsWith("templates/")
                    ? resourcePath.substring("templates/".length())
                    : resourcePath;
            usedCustomTemplates.add(displayPath);
            return Files.readString(customTemplate);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to load custom template: " + customTemplate,
                    e);
        }
    }

    private String loadTemplate(Path templateDir, String path) {
        String customContent = loadCustomTemplate(templateDir, path);
        if (customContent != null) {
            return customContent;
        }
        try (InputStream in = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Template not found on classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load template: " + path, e);
        }
    }

    public void printTemplateSummary() {

        if (!customTemplateDirectoryProvided) {
            return;
        }

        System.out.println("▶ Template override directory: " + configuredTemplateDir);

        if (usedCustomTemplates.isEmpty()) {

            System.out.println(
                    "⚠ No matching custom templates found. " +
                            "All templates were loaded from built-in defaults."
            );

            return;
        }

        System.out.println(
                "✔ Custom templates used (" +
                        usedCustomTemplates.size() +
                        "):"
        );

        usedCustomTemplates.forEach(t ->
                System.out.println("   - " + t));

        System.out.println(
                "ℹ Remaining templates were loaded from built-in defaults."
        );
    }
}
