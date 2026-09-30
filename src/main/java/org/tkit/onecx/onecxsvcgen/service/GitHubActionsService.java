package org.tkit.onecx.onecxsvcgen.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@ApplicationScoped
public class GitHubActionsService {

    @Inject
    TemplateService templates;

    public void generate(Path projectPath, Map<String, Object> ctx, Path templateDir) {

        try {
            Path github = projectPath.resolve(".github");
            Path workflows = github.resolve("workflows");

            Files.createDirectories(workflows);

            // workflows
            render("build.yml.tpl", workflows, "build.yml", ctx, templateDir);
            render("build-branch.yml.tpl", workflows, "build-branch.yml", ctx, templateDir);
            render("build-pr.yml.tpl", workflows, "build-pr.yml", ctx, templateDir);
            render("build-pr-merge.yml.tpl", workflows, "build-pr-merge.yml", ctx, templateDir);
            render("build-release.yml.tpl", workflows, "build-release.yml", ctx, templateDir);

            render("create-fix-branch.yml.tpl", workflows, "create-fix-branch.yml", ctx, templateDir);
            render("create-new-build.yml.tpl", workflows, "create-new-build.yml", ctx, templateDir);
            render("create-release.yml.tpl", workflows, "create-release.yml", ctx, templateDir);

            render("documentation.yml.tpl", workflows, "documentation.yml", ctx, templateDir);
            render("security.yml.tpl", workflows, "security.yml", ctx, templateDir);
            render("sonar-pr.yml.tpl", workflows, "sonar-pr.yml", ctx, templateDir);

            // renovate
            templates.renderToFile(templateDir,
                    "templates/github/renovate.json.tpl",
                    github.resolve("renovate.json"),
                    ctx
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate GitHub Actions", e);
        }
    }

    private void render(String template, Path dir, String target, Map<String, Object> ctx, Path templateDir) {
        templates.renderToFile(templateDir,
                "templates/github/workflows/" + template,
                dir.resolve(target),
                ctx
        );
    }
}