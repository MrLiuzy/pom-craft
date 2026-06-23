package com.github.mrliuzy.pomcraft.resolver;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class WorkspaceAwareModelResolver implements ModelResolver {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceAwareModelResolver.class);

    private final MavenSystemFactory systemFactory;
    private final RepositorySystem repoSystem;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> remoteRepositories;

    public WorkspaceAwareModelResolver(MavenSystemFactory systemFactory) {
        this.systemFactory = systemFactory;
        this.repoSystem = systemFactory.getRepositorySystem();
        this.session = systemFactory.getSession();
        this.remoteRepositories = systemFactory.getRemoteRepositories();
    }

    @Override
    public ModelSource resolveModel(String groupId, String artifactId, String version)
        throws UnresolvableModelException {
        return doResolveModel(groupId, artifactId, version);
    }

    @Override
    public ModelSource resolveModel(Parent parent) throws UnresolvableModelException {
        return doResolveModel(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
    }

    @Override
    public ModelSource resolveModel(Dependency dependency) throws UnresolvableModelException {
        return doResolveModel(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
    }

    private ModelSource doResolveModel(String groupId, String artifactId, String version)
        throws UnresolvableModelException {

        LOG.debug("Resolving model: {}:{}:{}", groupId, artifactId, version);

        File workspacePom = findInWorkspace(groupId, artifactId, version);
        if (workspacePom != null) {
            LOG.debug("Resolved from workspace: {}:{}:{} -> {}", groupId, artifactId, version, workspacePom);
            return new FileModelSource(workspacePom);
        }

        File localPom = findInLocalRepo(groupId, artifactId, version);
        if (localPom != null) {
            LOG.debug("Resolved from local repo: {}:{}:{}", groupId, artifactId, version);
            return new FileModelSource(localPom);
        }

        if (!session.isOffline()) {
            File remotePom = findInRemoteRepos(groupId, artifactId, version);
            if (remotePom != null) {
                LOG.debug("Resolved from remote: {}:{}:{}", groupId, artifactId, version);
                return new FileModelSource(remotePom);
            }
        }

        throw new UnresolvableModelException(
            "Could not resolve model " + groupId + ":" + artifactId + ":" + version,
            groupId, artifactId, version);
    }

    @Override
    public void addRepository(Repository repository) throws InvalidRepositoryException {
        LOG.debug("Adding repository: {} ({})", repository.getId(), repository.getUrl());
    }

    @Override
    public void addRepository(Repository repository, boolean replace) throws InvalidRepositoryException {
        LOG.debug("Adding repository: {} ({}) replace={}", repository.getId(), repository.getUrl(), replace);
    }

    @Override
    public ModelResolver newCopy() {
        return new WorkspaceAwareModelResolver(systemFactory);
    }

    private File findInWorkspace(String groupId, String artifactId, String version) {
        WorkspaceProjectReader reader = systemFactory.getWorkspaceReader();
        org.eclipse.aether.artifact.Artifact artifact =
            new DefaultArtifact(groupId, artifactId, "pom", version);
        File projectDir = reader.findArtifact(artifact);
        if (projectDir != null) {
            File pomFile = new File(projectDir, "pom.xml");
            if (pomFile.isFile()) {
                return pomFile;
            }
        }
        return null;
    }

    private File findInLocalRepo(String groupId, String artifactId, String version) {
        String localRepo = session.getLocalRepository().getBasedir().getAbsolutePath();
        String path = groupId.replace('.', '/')
            + '/' + artifactId
            + '/' + version
            + '/' + artifactId + '-' + version + ".pom";
        File pomFile = new File(localRepo, path);
        return pomFile.isFile() ? pomFile : null;
    }

    private File findInRemoteRepos(String groupId, String artifactId, String version) {
        try {
            org.eclipse.aether.artifact.Artifact artifact =
                new DefaultArtifact(groupId, artifactId, "pom", version);
            ArtifactRequest request = new ArtifactRequest();
            request.setArtifact(artifact);
            request.setRepositories(remoteRepositories);

            ArtifactResult result = repoSystem.resolveArtifact(session, request);

            if (result.isResolved()) {
                return result.getArtifact().getFile();
            }
        } catch (ArtifactResolutionException e) {
            LOG.debug("Failed to resolve {}:{}:{} from remote repos: {}",
                groupId, artifactId, version, e.getMessage());
        }
        return null;
    }
}
