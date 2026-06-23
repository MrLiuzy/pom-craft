package com.github.mrliuzy.pomcraft.resolver;

import com.github.mrliuzy.pomcraft.config.PomParserConfig;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.DefaultAuthenticationSelector;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.eclipse.aether.util.repository.DefaultProxySelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MavenSystemFactory {

    private static final Logger LOG = LoggerFactory.getLogger(MavenSystemFactory.class);

    private final PomParserConfig config;
    private final WorkspaceProjectReader workspaceReader;

    private DefaultServiceLocator serviceLocator;
    private RepositorySystem repositorySystem;
    private DefaultRepositorySystemSession session;
    private ModelBuilder modelBuilder;
    private org.apache.maven.settings.Settings effectiveSettings;

    public MavenSystemFactory(PomParserConfig config) {
        this.config = config;
        this.workspaceReader = new WorkspaceProjectReader(config.getWorkspaceDirectories());
    }

    public RepositorySystem getRepositorySystem() {
        if (repositorySystem == null) {
            repositorySystem = createRepositorySystem();
        }
        return repositorySystem;
    }

    public DefaultRepositorySystemSession getSession() {
        if (session == null) {
            session = createSession();
        }
        return session;
    }

    public ModelBuilder getModelBuilder() {
        if (modelBuilder == null) {
            modelBuilder = createModelBuilder();
        }
        return modelBuilder;
    }

    public org.apache.maven.settings.Settings getEffectiveSettings() {
        if (effectiveSettings == null) {
            effectiveSettings = buildSettings();
        }
        return effectiveSettings;
    }

    public WorkspaceProjectReader getWorkspaceReader() {
        return workspaceReader;
    }

    private RepositorySystem createRepositorySystem() {
        serviceLocator = MavenRepositorySystemUtils.newServiceLocator();
        serviceLocator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        serviceLocator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        RepositorySystem system = serviceLocator.getService(RepositorySystem.class);
        if (system == null) {
            throw new IllegalStateException("Failed to create RepositorySystem via service locator");
        }
        return system;
    }

    private DefaultRepositorySystemSession createSession() {
        getRepositorySystem();

        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        session.setWorkspaceReader(workspaceReader);
        session.setOffline(config.isOffline());

        org.apache.maven.settings.Settings settings = getEffectiveSettings();

        String localRepoPath = settings.getLocalRepository();
        if (localRepoPath == null || localRepoPath.isEmpty()) {
            localRepoPath = System.getProperty("user.home") + File.separator
                + ".m2" + File.separator + "repository";
        }
        LocalRepository localRepo = new LocalRepository(localRepoPath);
        session.setLocalRepositoryManager(
            getRepositorySystem().newLocalRepositoryManager(session, localRepo));

        session.setMirrorSelector(buildMirrorSelector(settings));
        session.setProxySelector(buildProxySelector(settings));
        session.setAuthenticationSelector(buildAuthenticationSelector(settings));

        session.setTransferListener(new LoggingTransferListener());
        session.setRepositoryListener(new LoggingRepositoryListener());

        return session;
    }

    private ModelBuilder createModelBuilder() {
        return new DefaultModelBuilderFactory().newInstance();
    }

    private org.apache.maven.settings.Settings buildSettings() {
        SettingsBuilder settingsBuilder = new DefaultSettingsBuilderFactory().newInstance();
        SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();

        if (config.getSettingsXml() != null && config.getSettingsXml().isFile()) {
            request.setUserSettingsFile(config.getSettingsXml());
        } else {
            File defaultUserSettings = new File(
                System.getProperty("user.home"), ".m2/settings.xml");
            if (defaultUserSettings.isFile()) {
                request.setUserSettingsFile(defaultUserSettings);
            }
        }

        String mavenHome = System.getenv("MAVEN_HOME");
        if (mavenHome != null) {
            File globalSettings = new File(mavenHome, "conf/settings.xml");
            if (globalSettings.isFile()) {
                request.setGlobalSettingsFile(globalSettings);
            }
        }

        try {
            SettingsBuildingResult result = settingsBuilder.build(request);
            return result.getEffectiveSettings();
        } catch (SettingsBuildingException e) {
            LOG.warn("Failed to build settings: {}. Using defaults.", e.getMessage());
            return new org.apache.maven.settings.Settings();
        }
    }

    public List<RemoteRepository> getRemoteRepositories() {
        List<RemoteRepository> repos = new ArrayList<>();
        repos.add(new RemoteRepository.Builder("central", "default",
            "https://repo.maven.apache.org/maven2").build());

        org.apache.maven.settings.Settings settings = getEffectiveSettings();
        for (Profile profile : settings.getProfiles()) {
            if (isProfileActive(profile, settings)) {
                for (Repository repo : profile.getRepositories()) {
                    repos.add(new RemoteRepository.Builder(
                        repo.getId(), "default", repo.getUrl()).build());
                }
            }
        }

        return repos;
    }

    private boolean isProfileActive(Profile profile, org.apache.maven.settings.Settings settings) {
        List<String> activeProfiles = settings.getActiveProfiles();
        return activeProfiles != null && activeProfiles.contains(profile.getId());
    }

    private MirrorSelector buildMirrorSelector(org.apache.maven.settings.Settings settings) {
        DefaultMirrorSelector selector = new DefaultMirrorSelector();
        if (settings.getMirrors() != null) {
            for (Mirror mirror : settings.getMirrors()) {
                selector.add(
                    mirror.getId(),
                    mirror.getUrl(),
                    mirror.getLayout(),
                    false,
                    mirror.getMirrorOf(),
                    mirror.getMirrorOfLayouts());
            }
        }
        return selector;
    }

    private ProxySelector buildProxySelector(org.apache.maven.settings.Settings settings) {
        DefaultProxySelector selector = new DefaultProxySelector();
        if (settings.getProxies() != null) {
            for (Proxy proxy : settings.getProxies()) {
                if (proxy.isActive()) {
                    org.eclipse.aether.repository.Proxy aetherProxy =
                        new org.eclipse.aether.repository.Proxy(
                            proxy.getProtocol(),
                            proxy.getHost(),
                            proxy.getPort(),
                            buildAuthentication(proxy.getUsername(), proxy.getPassword()));
                    selector.add(aetherProxy, proxy.getNonProxyHosts());
                }
            }
        }
        return selector;
    }

    private org.eclipse.aether.repository.AuthenticationSelector buildAuthenticationSelector(
        org.apache.maven.settings.Settings settings) {
        DefaultAuthenticationSelector selector = new DefaultAuthenticationSelector();
        if (settings.getServers() != null) {
            for (Server server : settings.getServers()) {
                Authentication auth = buildAuthentication(
                    server.getUsername(), server.getPassword());
                if (auth != null) {
                    selector.add(server.getId(), auth);
                }
            }
        }
        return selector;
    }

    private Authentication buildAuthentication(String username, String password) {
        if (username != null && password != null) {
            return new AuthenticationBuilder()
                .addUsername(username)
                .addPassword(password)
                .build();
        }
        return null;
    }
}
