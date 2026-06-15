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

    public void generate(Path projectPath, Map<String, Object> ctx) {

        try {
            Path github = projectPath.resolve(".github");
            Path workflows = github.resolve("workflows");

            Files.createDirectories(workflows);

            // workflows
            render("build.yml.tpl", workflows, "build.yml", ctx);
            render("build-branch.yml.tpl", workflows, "build-branch.yml", ctx);
            render("build-pr.yml.tpl", workflows, "build-pr.yml", ctx);
            render("build-pr-merge.yml.tpl", workflows, "build-pr-merge.yml", ctx);
            render("build-release.yml.tpl", workflows, "build-release.yml", ctx);

            render("create-fix-branch.yml.tpl", workflows, "create-fix-branch.yml", ctx);
            render("create-new-build.yml.tpl", workflows, "create-new-build.yml", ctx);
            render("create-release.yml.tpl", workflows, "create-release.yml", ctx);

            render("documentation.yml.tpl", workflows, "documentation.yml", ctx);
            render("security.yml.tpl", workflows, "security.yml", ctx);
            render("sonar-pr.yml.tpl", workflows, "sonar-pr.yml", ctx);

            // dependabot
            templates.renderToFile(
                    "templates/github/dependabot.yml.tpl",
                    github.resolve("dependabot.yml"),
                    ctx
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate GitHub Actions", e);
        }
    }

    private void render(String template, Path dir, String target, Map<String, Object> ctx) {
        templates.renderToFile(
                "templates/github/workflows/" + template,
                dir.resolve(target),
                ctx
        );
    }
}