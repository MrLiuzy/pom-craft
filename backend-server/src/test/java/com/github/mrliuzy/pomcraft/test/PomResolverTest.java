package com.github.mrliuzy.pomcraft.test;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.mrliuzy.pomcraft.config.PomParserConfig;
import com.github.mrliuzy.pomcraft.model.ConflictInfo;
import com.github.mrliuzy.pomcraft.model.DependencyInfo;
import com.github.mrliuzy.pomcraft.model.ParsedPomResult;
import com.github.mrliuzy.pomcraft.resolver.MavenPomResolver;

public class PomResolverTest {

    public static void main(String[] args) {
        PomParserConfig config = new PomParserConfig();
        config.setWorkspaceDirectories(Arrays.asList(
            new File(PomResolverTest.class.getResource("/dep-a").getFile()),
            new File(PomResolverTest.class.getResource("/dep-a-a").getFile())
        ));
        config.setTargetPom(new File(PomResolverTest.class.getResource("/dep-b").getFile(), "pom.xml"));
        MavenPomResolver resolver = new MavenPomResolver(config);
        System.out.println("----------------------");
        ParsedPomResult result = resolver.resolve();
        System.out.println("result:" + result.isSuccess());
        if(result.isSuccess()){
            printResult(result);
        }
        else {
            System.err.println(result.getErrorMessage());
        }
    }

    private static void printResult(ParsedPomResult result) {
        System.out.println("=== All Dependencies (" + result.getAllDependencies().size() + ") ===");
        Map<String, String> gaVersionMap = new HashMap<>();
        for (DependencyInfo dep : result.getAllDependencies()) {
            System.out.printf("  %s:%s:%s  (scope=%s, optional=%s)%n",
                dep.getGroupId(), dep.getArtifactId(), dep.getVersion(),
                dep.getScope(), dep.isOptional());
            gaVersionMap.put(dep.getGroupId()+":"+dep.getArtifactId(), dep.getVersion());
        }

        System.out.println();
        System.out.println("=== Direct Dependencies (" + result.getDirectDependencies().size() + ") ===");
        try{
            PrintWriter writer = new PrintWriter(System.out);
            printDepTree(result.getDirectDependencies(), "  ", gaVersionMap, writer);
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
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

    private static void printDepTree(List<DependencyInfo> deps, String indent, Map<String, String> gaVersionMap, PrintWriter writer) {
        for (DependencyInfo dep : deps) {
            String version = gaVersionMap.get(dep.getGroupId()+":"+dep.getArtifactId());
            if(!dep.getVersion().equals(version)){
                if(dep.getManagedVersion()!=null){
                    writer.printf("%s%s:%s:%s  (scope=%s, optional=%s, type=%s, resolved=%s, managed from %s)%n",
                    indent, dep.getGroupId(), dep.getArtifactId(), dep.getManagedVersion(),
                    dep.getScope(), dep.isOptional(), dep.getType(),dep.isResolved(), dep.getVersion());
                }
                else {
                    writer.printf("%s%s:%s:%s  (scope=%s, optional=%s, type=%s, resolved=%s, omited for conflict with %s)%n",
                    indent, dep.getGroupId(), dep.getArtifactId(), dep.getVersion(),
                    dep.getScope(), dep.isOptional(), dep.getType(),dep.isResolved(), version);
                }
            }
            else {
                writer.printf("%s%s:%s:%s  (scope=%s, optional=%s, type=%s, resolved=%s)%n",
                    indent, dep.getGroupId(), dep.getArtifactId(), dep.getVersion(),
                    dep.getScope(), dep.isOptional(), dep.getType(),dep.isResolved());
            }
            if(!dep.isResolved()){
                System.err.println(dep.getErrorMessage());
            }
            if (!dep.getChildren().isEmpty()) {
                printDepTree(dep.getChildren(), indent + "  ", gaVersionMap, writer);
            }
        }
    }
}
