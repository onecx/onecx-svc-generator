package org.tkit.onecx.onecxsvcgen.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class BuildService {

    public void runMavenBuild(Path projectPath) {
        runMaven(projectPath, "clean", "package", "-DskipTests");
    }

    public void runLiquibaseDiff(Path projectPath) {
        runMaven(projectPath, "clean", "compile", "-Pdb-diff", "-DskipTests");
    }

    private void runMaven(Path projectPath, String... args) {
        try {
            Path normalized = projectPath.toAbsolutePath().normalize();

            List<String> command = new ArrayList<>();
            Path mvnw = normalized.resolve("mvnw");

            if (Files.exists(mvnw)) {
                command.add("./mvnw");
                command.addAll(Arrays.asList(args));
            } else if (isWindows()) {
                command.add("cmd");
                command.add("/c");
                command.add("mvn " + String.join(" ", args));
            } else {
                command.add("bash");
                command.add("-lc");
                command.add("mvn " + String.join(" ", args));
            }

            System.out.println("▶ Running Maven build in: " + normalized);
            System.out.println("▶ Command: " + String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(normalized.toFile());
            pb.inheritIO();

            Process process = pb.start();
            int exit = process.waitFor();

            if (exit != 0) {
                throw new RuntimeException("Maven build failed with exit code: " + exit);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to run Maven build in: " + projectPath, e);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}