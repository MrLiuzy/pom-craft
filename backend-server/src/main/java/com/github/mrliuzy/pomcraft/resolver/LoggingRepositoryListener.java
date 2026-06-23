package com.github.mrliuzy.pomcraft.resolver;

import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.AbstractRepositoryListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LoggingRepositoryListener extends AbstractRepositoryListener {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingRepositoryListener.class);

    @Override
    public void artifactDeployed(RepositoryEvent event) {
        LOG.info("Deployed {} to {}", event.getArtifact(), event.getRepository());
    }

    @Override
    public void artifactDeploying(RepositoryEvent event) {
        LOG.info("Deploying {} to {}", event.getArtifact(), event.getRepository());
    }

    @Override
    public void artifactDownloaded(RepositoryEvent event) {
        LOG.info("Downloaded {} from {}", event.getArtifact(), event.getRepository());
    }

    @Override
    public void artifactDownloading(RepositoryEvent event) {
        LOG.info("Downloading {} from {}", event.getArtifact(), event.getRepository());
    }

    @Override
    public void artifactResolved(RepositoryEvent event) {
        LOG.debug("Resolved {}", event.getArtifact());
    }
}
