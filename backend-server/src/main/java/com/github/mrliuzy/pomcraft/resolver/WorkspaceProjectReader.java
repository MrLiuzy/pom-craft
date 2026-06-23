package com.github.mrliuzy.pomcraft.resolver;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkspaceProjectReader implements WorkspaceReader {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceProjectReader.class);

    private final WorkspaceRepository repository;
    private final Map<String, File> artifactsByGav = new HashMap<>();
    private final Map<String, SortedMap<String, File>> artifactsByGa = new HashMap<>();

    public WorkspaceProjectReader(List<File> workspaceDirectories) {
        this.repository = new WorkspaceRepository("workspace", "workspace");
        if (workspaceDirectories != null && !workspaceDirectories.isEmpty()) {
            scanWorkspaceDirectories(workspaceDirectories);
        }
    }

    private void scanWorkspaceDirectories(List<File> directories) {
        for (File dir : directories) {
            if (!dir.isDirectory()) {
                LOG.warn("Workspace path is not a directory, skipping: {}", dir);
                continue;
            }
            scanDirectory(dir);
        }
        LOG.info("Workspace scan complete: {} projects indexed", artifactsByGav.size());
    }

    private void scanDirectory(File directory) {
        try (Stream<Path> stream = Files.walk(directory.toPath(), Integer.MAX_VALUE)) {
            stream
                .filter(p -> p.getFileName().toString().equals("pom.xml"))
                .forEach(this::indexPom);
        } catch (Exception e) {
            LOG.warn("Error scanning workspace directory {}: {}", directory, e.getMessage());
        }
    }

    private void indexPom(Path pomPath) {
        try {
            File pomFile = pomPath.toFile();
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model;
            try (FileReader fr = new FileReader(pomFile)) {
                model = reader.read(fr);
            }

            String groupId = model.getGroupId();
            String artifactId = model.getArtifactId();
            String version = model.getVersion();

            if (groupId == null && model.getParent() != null) {
                groupId = model.getParent().getGroupId();
            }
            if (version == null && model.getParent() != null) {
                version = model.getParent().getVersion();
            }

            if (groupId == null || artifactId == null || version == null) {
                LOG.debug("Skipping {} (incomplete GAV)", pomFile);
                return;
            }

            if (version.startsWith("${")) {
                String resolved = resolveSimpleProperty(version, model);
                if (resolved != null && !resolved.startsWith("${")) {
                    version = resolved;
                } else {
                    LOG.debug("Skipping {} (unresolved version property: {})", pomFile, version);
                    return;
                }
            }

            String gav = groupId + ":" + artifactId + ":" + version;
            String ga = groupId + ":" + artifactId;

            artifactsByGav.put(gav, pomFile);
            artifactsByGa
                .computeIfAbsent(ga, k -> new TreeMap<>())
                .put(version, pomFile);

            LOG.debug("Indexed workspace project: {} -> {}", gav, pomFile);

        } catch (Exception e) {
            LOG.warn("Failed to parse workspace POM {}: {}", pomPath, e.getMessage());
        }
    }

    private String resolveSimpleProperty(String value, Model model) {
        if (!value.startsWith("${") || !value.endsWith("}")) {
            return null;
        }
        String key = value.substring(2, value.length() - 1);
        if (model.getProperties() != null) {
            String resolved = model.getProperties().getProperty(key);
            if (resolved != null) return resolved;
        }
        return value;
    }

    @Override
    public WorkspaceRepository getRepository() {
        return repository;
    }

    @Override
    public File findArtifact(Artifact artifact) {
        String gav = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
        File pomFile = artifactsByGav.get(gav);
        if (pomFile != null) {
            LOG.debug("Workspace resolved: {} -> {}", gav, pomFile.getParentFile());
            if ("pom".equals(artifact.getExtension())) {
                return pomFile;
            }
            return pomFile.getParentFile();
        }
        return null;
    }

    @Override
    public List<String> findVersions(Artifact artifact) {
        String ga = artifact.getGroupId() + ":" + artifact.getArtifactId();
        SortedMap<String, File> versions = artifactsByGa.get(ga);
        if (versions != null) {
            return new ArrayList<>(versions.keySet());
        }
        return Collections.emptyList();
    }

    public Model findModel(Artifact artifact) {
        String gav = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
        File pomFile = artifactsByGav.get(gav);
        if (pomFile == null) {
            String ga = artifact.getGroupId() + ":" + artifact.getArtifactId();
            SortedMap<String, File> versions = artifactsByGa.get(ga);
            if (versions != null && !versions.isEmpty()) {
                pomFile = versions.get(versions.lastKey());
            }
        }
        if (pomFile != null) {
            try (FileReader fr = new FileReader(pomFile)) {
                return new MavenXpp3Reader().read(fr);
            } catch (Exception e) {
                LOG.warn("Failed to read model: {}", e.getMessage());
            }
        }
        return null;
    }

    public List<String> getAllGAV(){
        return new ArrayList<String>(this.artifactsByGav.keySet());
    }
}
