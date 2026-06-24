package com.github.mrliuzy.pomcraft.resolver;

import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.AbstractRepositoryListener;
import com.github.mrliuzy.pomcraft.Log;
import com.github.mrliuzy.pomcraft.Log.Logger;

class LoggingRepositoryListener extends AbstractRepositoryListener {

    private static final Logger LOG = Log.getLogger(LoggingRepositoryListener.class);

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
