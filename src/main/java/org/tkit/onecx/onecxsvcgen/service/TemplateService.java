package org.tkit.onecx.onecxsvcgen.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
public class TemplateService {

    public void renderToFile(String resourcePath, Path target, Map<String, ?> ctx) {
        String content = loadResource(resourcePath);
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

    private String loadResource(String path) {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Template not found on classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load template: " + path, e);
        }
    }
}
