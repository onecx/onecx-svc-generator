package org.tkit.onecx.onecxsvcgen.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class NamingService {

    private static final Map<String, String> ABBREVIATIONS = Map.ofEntries(
            Map.entry("workspace", "ws"),
            Map.entry("user-profile", "up"),
            Map.entry("file-storage", "fs")
    );

    public String scopePrefixFromArtifactId(String artifactId) {
        String slug = artifactSlug(artifactId);
        String suffix = ABBREVIATIONS.getOrDefault(slug, slug);
        return "ocx-" + suffix;
    }

    public String artifactSlug(String artifactId) {
        String slug = artifactId;
        if (slug.startsWith("onecx-")) {
            slug = slug.substring("onecx-".length());
        }
        if (slug.endsWith("-svc")) {
            slug = slug.substring(0, slug.length() - "-svc".length());
        }
        return slug;
    }

    public String kebab(String name) {
        if (name == null || name.isBlank()) return name;
        String s = name.replaceAll("([a-z])([A-Z])", "$1-$2").replace('_', '-');
        return s.toLowerCase(Locale.ROOT);
    }

    public String lowerCamel(String name) {
        if (name == null || name.isBlank()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    public String pluralPath(String entityName) {
        String base = kebab(entityName);
        if (base.endsWith("y") && base.length() > 1) {
            return base.substring(0, base.length() - 1) + "ies";
        }
        if (base.endsWith("s")) {
            return base + "es";
        }
        return base + "s";
    }

    public String upperFirst(String value) {
        if (value == null || value.isBlank()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    public String apiInterfaceName(String tag) {
        return upperFirst(lowerCamel(tag)) + "Api";
    }
}
