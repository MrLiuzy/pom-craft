package com.github.mrliuzy.pomcraft.resolver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelBuildingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mrliuzy.pomcraft.config.PomParserConfig;
import com.github.mrliuzy.pomcraft.model.ConflictInfo;
import com.github.mrliuzy.pomcraft.model.DependencyInfo;
import com.github.mrliuzy.pomcraft.model.ParsedPomResult;

public class MavenPomResolver {

    private static final Logger LOG = LoggerFactory.getLogger(MavenPomResolver.class);

    private final PomParserConfig config;
    private final MavenSystemFactory systemFactory;
    private final ModelBuilder modelBuilder;
    private final WorkspaceAwareModelResolver modelResolver;

    public MavenPomResolver(PomParserConfig config) {
        this.config = config;
        config.validate();
        this.systemFactory = new MavenSystemFactory(config);
        this.modelBuilder = systemFactory.getModelBuilder();
        this.modelResolver = new WorkspaceAwareModelResolver(systemFactory);
    }

    public ParsedPomResult resolve() {
        ParsedPomResult result = new ParsedPomResult();

        try {
            Model effectiveModel = buildEffectiveModel(config.getTargetPom());

            // Build managed version map from root POM's dependencyManagement
            Map<String, String> managedVersions = new LinkedHashMap<>();
            if (effectiveModel.getDependencyManagement() != null
                && effectiveModel.getDependencyManagement().getDependencies() != null) {
                for (Dependency dm : effectiveModel.getDependencyManagement().getDependencies()) {
                    if (dm.getGroupId() != null && dm.getArtifactId() != null && dm.getVersion() != null) {
                        managedVersions.put(dm.getGroupId() + ":" + dm.getArtifactId(), dm.getVersion());
                    }
                }
            }

            List<DependencyInfo> directDeps = extractDirectDependencies(effectiveModel);

            List<DependencyInfo> allDeps = new LinkedList<>();
            Map<String, Set<String>> conflictTracker = new LinkedHashMap<>();
            Map<String, String> gaWinner = new LinkedHashMap<>();

            for (DependencyInfo dep : directDeps) {
                String ga = dep.gaKey();
                String ver = managedVersions.getOrDefault(ga, dep.getVersion());
                dep.setVersion(ver);
                gaWinner.put(ga, ver);
                allDeps.add(dep);
                conflictTracker.computeIfAbsent(ga, k -> new LinkedHashSet<>()).add(ver);
            }

            Queue<DependencyInfo> queue = new LinkedList<>(directDeps);

            while (!queue.isEmpty()) {
                DependencyInfo current = queue.poll();
                Model depModel = resolveDependencyModel(current);
                if (depModel == null) {
                    current.setResolved(false);
                    continue;
                }
                if (depModel.getDependencies() == null) {
                    continue;
                }

                for (Dependency child : depModel.getDependencies()) {
                    String scope = child.getScope() != null ? child.getScope() : "compile";
                    if (isExcludedScope(scope) || child.isOptional()) {
                        continue;
                    }

                    DependencyInfo childInfo = toDependencyInfo(child);
                    String ga = childInfo.gaKey();
                    String declaredVer = childInfo.getVersion();
                    String managedVer = managedVersions.get(ga);

                    conflictTracker.computeIfAbsent(ga, k -> new LinkedHashSet<>()).add(declaredVer);
                    if (managedVer != null && !managedVer.equals(declaredVer)) {
                        conflictTracker.get(ga).add(managedVer);
                        childInfo.setManagedVersion(managedVer);
                    }
                    current.getChildren().add(childInfo);

                    String effectiveVer = managedVer != null ? managedVer : declaredVer;
                    String existingVer = gaWinner.get(ga);
                    if (existingVer == null) {
                        gaWinner.put(ga, effectiveVer);
                        DependencyInfo allDepEntry = childInfo;
                        if (managedVer != null && !managedVer.equals(declaredVer)) {
                            allDepEntry = new DependencyInfo(
                                childInfo.getGroupId(), childInfo.getArtifactId(), managedVer,
                                childInfo.getScope(), childInfo.isOptional(),
                                childInfo.getType(), childInfo.getClassifier());
                        }
                        allDeps.add(allDepEntry);
                        queue.add(childInfo);
                    }
                }
            }

            result.setDirectDependencies(directDeps);
            result.setAllDependencies(allDeps);
            result.setConflicts(buildConflicts(conflictTracker, gaWinner));
            result.setSuccess(true);

        } catch (Exception e) {
            LOG.error("Unexpected error during resolution", e);
            result.setSuccess(false);
            ByteArrayOutputStream bsOut = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(bsOut));
            result.setErrorMessage(new String(bsOut.toByteArray()));
        }

        return result;
    }

    public String getEffectivePomXml() {
        try {
            Model model = buildEffectiveModel(config.getTargetPom());
            if (model == null) return null;
            java.io.StringWriter sw = new java.io.StringWriter();
            new org.apache.maven.model.io.xpp3.MavenXpp3Writer().write(sw, model);
            return sw.toString();
        } catch (Exception e) {
            LOG.error("Failed to get effective POM", e);
            return null;
        }
    }

    Model buildEffectiveModel(File pomFile) throws ModelBuildingException {
        try {
            ModelBuildingRequest req = new DefaultModelBuildingRequest();
            req.setPomFile(pomFile);
            req.setModelResolver(modelResolver.newCopy());
            req.setSystemProperties(System.getProperties());
            req.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);

            ModelBuildingResult result = modelBuilder.build(req);
            return result.getEffectiveModel();
        } catch (ModelBuildingException e) {
            LOG.warn("Failed to build effective model for {}: {}", pomFile, e.getMessage());
            throw e;
        }
    }

    private List<DependencyInfo> extractDirectDependencies(Model effectiveModel) {
        List<DependencyInfo> deps = new LinkedList<>();
        if (effectiveModel.getDependencies() != null) {
            for (Dependency dep : effectiveModel.getDependencies()) {
                deps.add(toDependencyInfo(dep));
            }
        }
        return deps;
    }

    private Model resolveDependencyModel(DependencyInfo dep) {
        File pomFile = resolvePomFile(dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
        if (pomFile == null) {
            dep.setResolved(false);
            dep.setErrorMessage("POM not found: " + dep.gav());
            return null;
        }

        try {
            Model effectiveModel = buildEffectiveModel(pomFile);
            if (effectiveModel != null) {
                return effectiveModel;
            }
        } catch (Exception e) {
            dep.setResolved(false);
            dep.setErrorMessage("Failed to build effective model: " + e.getMessage());
            return null;
        }

        try {
            return buildRawModel(pomFile);
        } catch (Exception e) {
            dep.setResolved(false);
            dep.setErrorMessage("Failed to parse POM: " + e.getMessage());
            return null;
        }
    }

    private Model buildRawModel(File pomFile) {
        try (java.io.FileReader fr = new java.io.FileReader(pomFile)) {
            return new org.apache.maven.model.io.xpp3.MavenXpp3Reader().read(fr);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private File resolvePomFile(String groupId, String artifactId, String version) {
        WorkspaceProjectReader reader = systemFactory.getWorkspaceReader();
        org.eclipse.aether.artifact.Artifact artifact =
            new org.eclipse.aether.artifact.DefaultArtifact(groupId, artifactId, "pom", version);
        File resolved = reader.findArtifact(artifact);
        if (resolved != null) {
            if (resolved.getName().equals("pom.xml") && resolved.isFile()) {
                return resolved;
            }
            if (resolved.isDirectory()) {
                File pomFile = new File(resolved, "pom.xml");
                if (pomFile.isFile()) {
                    return pomFile;
                }
            }
        }

        String localRepo = systemFactory.getSession().getLocalRepository().getBasedir().getAbsolutePath();
        String path = groupId.replace('.', '/')
            + '/' + artifactId
            + '/' + version
            + '/' + artifactId + '-' + version + ".pom";
        File pomFile = new File(localRepo, path);
        if (pomFile.isFile()) {
            return pomFile;
        }

        if (!config.isOffline()) {
            File remotePom = downloadPomFromRemote(groupId, artifactId, version);
            if (remotePom != null) {
                return remotePom;
            }
        }

        File fallback = findPomInWorkspaceDirectories(artifactId);
        if (fallback != null) {
            return fallback;
        }
        return null;
    }

    private File findPomInWorkspaceDirectories(String artifactId) {
        for (File dir : config.getWorkspaceDirectories()) {
            if (!dir.isDirectory()) {
                continue;
            }
            if (dir.getName().equals(artifactId)) {
                File pomFile = new File(dir, "pom.xml");
                if (pomFile.isFile()) {
                    return pomFile;
                }
            }
        }
        return null;
    }

    private File downloadPomFromRemote(String groupId, String artifactId, String version) {
        try {
            org.eclipse.aether.RepositorySystem repoSystem = systemFactory.getRepositorySystem();
            org.eclipse.aether.DefaultRepositorySystemSession session = systemFactory.getSession();
            org.eclipse.aether.artifact.Artifact artifact =
                new org.eclipse.aether.artifact.DefaultArtifact(groupId, artifactId, "pom", version);
            org.eclipse.aether.resolution.ArtifactRequest request =
                new org.eclipse.aether.resolution.ArtifactRequest();
            request.setArtifact(artifact);
            request.setRepositories(systemFactory.getRemoteRepositories());

            org.eclipse.aether.resolution.ArtifactResult result =
                repoSystem.resolveArtifact(session, request);

            if (result.isResolved()) {
                return result.getArtifact().getFile();
            }
        } catch (org.eclipse.aether.resolution.ArtifactResolutionException e) {
            // download failed, skip
        }
        return null;
    }

    private boolean isExcludedScope(String scope) {
        return "test".equals(scope) || "provided".equals(scope) || "system".equals(scope);
    }

    private List<ConflictInfo> buildConflicts(Map<String, Set<String>> conflictTracker,
                                               Map<String, String> gaWinner) {
        List<ConflictInfo> conflicts = new LinkedList<>();
        for (Map.Entry<String, Set<String>> entry : conflictTracker.entrySet()) {
            if (entry.getValue().size() > 1) {
                String[] ga = entry.getKey().split(":", 2);
                String winnerVersion = gaWinner.get(entry.getKey());
                Set<String> conflictVersions = new LinkedHashSet<>(entry.getValue());
                conflictVersions.remove(winnerVersion);
                conflicts.add(new ConflictInfo(ga[0], ga[1], winnerVersion, conflictVersions));
            }
        }
        return conflicts;
    }

    private DependencyInfo toDependencyInfo(Dependency dep) {
        return new DependencyInfo(
            dep.getGroupId(),
            dep.getArtifactId(),
            dep.getVersion(),
            dep.getScope() != null ? dep.getScope() : "compile",
            dep.isOptional(),
            dep.getType() != null ? dep.getType() : "jar",
            dep.getClassifier()
        );
    }
}
