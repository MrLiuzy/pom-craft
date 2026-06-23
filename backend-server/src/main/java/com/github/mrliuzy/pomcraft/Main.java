package com.github.mrliuzy.pomcraft;

import com.github.mrliuzy.pomcraft.config.PomParserConfig;
import com.github.mrliuzy.pomcraft.model.ConflictInfo;
import com.github.mrliuzy.pomcraft.model.DependencyInfo;
import com.github.mrliuzy.pomcraft.model.ParsedPomResult;
import com.github.mrliuzy.pomcraft.resolver.MavenPomResolver;

import java.io.File;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            demo();
            return;
        }

        PomParserConfig config = parseArgs(args);
        MavenPomResolver resolver = new MavenPomResolver(config);
        ParsedPomResult result = resolver.resolve();
        printResult(result);
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar backend-server.jar <pom-file> [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --offline                  Run in offline mode (no remote repos)");
        System.out.println("  --skip-failures            Skip dependencies that fail to resolve");
        System.out.println("  --workspace <dir1,dir2>    Comma-separated workspace directories");
        System.out.println("  --settings <file>          Path to custom settings.xml");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -jar backend-server.jar pom.xml --offline --workspace ../other-project");
    }

    private static PomParserConfig parseArgs(String[] args) {
        PomParserConfig config = new PomParserConfig();
        config.setTargetPom(new File(args[0]));

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--offline":
                    config.setOffline(true);
                    break;
                case "--skip-failures":
                    config.setSkipFailedResolution(true);
                    break;
                case "--workspace":
                    if (i + 1 < args.length) {
                        for (String dir : args[++i].split(",")) {
                            config.getWorkspaceDirectories().add(new File(dir.trim()));
                        }
                    }
                    break;
                case "--settings":
                    if (i + 1 < args.length) {
                        config.setSettingsXml(new File(args[++i]));
                    }
                    break;
            }
        }

        return config;
    }

    private static void demo() {
        System.out.println();
        System.out.println("=== Demo: analyzing this project's pom.xml (offline mode) ===");
        System.out.println();

        PomParserConfig config = new PomParserConfig();
        config.setTargetPom(new File("pom.xml"));
        config.setOffline(true);

        MavenPomResolver resolver = new MavenPomResolver(config);
        ParsedPomResult result = resolver.resolve();

        printResult(result);
    }

    private static void printResult(ParsedPomResult result) {
        System.out.println("=== Direct Dependencies (" + result.getDirectDependencies().size() + ") ===");
        printDepTree(result.getDirectDependencies(), "  ");

        System.out.println();
        System.out.println("=== All Dependencies (" + result.getAllDependencies().size() + ") ===");
        for (DependencyInfo dep : result.getAllDependencies()) {
            System.out.printf("  %s:%s:%s  (scope=%s, optional=%s)%n",
                dep.getGroupId(), dep.getArtifactId(), dep.getVersion(),
                dep.getScope(), dep.isOptional());
        }

        System.out.println();
        System.out.println("=== Conflicts (" + result.getConflicts().size() + ") ===");
        if (result.getConflicts().isEmpty()) {
            System.out.println("  (none)");
        } else {
            for (ConflictInfo conflict : result.getConflicts()) {
                System.out.printf("  %s:%s  winner=%s  conflicting=%s%n",
                    conflict.getGroupId(), conflict.getArtifactId(),
                    conflict.getVersion(), conflict.getConflictingVersions());
            }
        }
    }

    private static void printDepTree(List<DependencyInfo> deps, String indent) {
        for (DependencyInfo dep : deps) {
            System.out.printf("%s%s:%s:%s  (scope=%s, optional=%s, type=%s)%n",
                indent, dep.getGroupId(), dep.getArtifactId(), dep.getVersion(),
                dep.getScope(), dep.isOptional(), dep.getType());
            if (!dep.getChildren().isEmpty()) {
                printDepTree(dep.getChildren(), indent + "  ");
            }
        }
    }
}
