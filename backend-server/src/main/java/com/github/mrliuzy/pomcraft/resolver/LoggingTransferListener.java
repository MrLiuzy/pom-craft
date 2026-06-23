package com.github.mrliuzy.pomcraft.resolver;

import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class LoggingTransferListener extends AbstractTransferListener {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingTransferListener.class);

    private final Map<TransferResource, Long> downloads = new ConcurrentHashMap<>();
    private int lastLength;

    @Override
    public void transferInitiated(TransferEvent event) {
        String msg = event.getRequestType() == TransferEvent.RequestType.PUT ? "Uploading" : "Downloading";
        LOG.info("{}: {} -> {}", msg, event.getResource().getRepositoryUrl(),
            event.getResource().getResourceName());
    }

    @Override
    public void transferSucceeded(TransferEvent event) {
        String msg = event.getRequestType() == TransferEvent.RequestType.PUT ? "Uploaded" : "Downloaded";
        LOG.info("{}: {} ({})", msg, event.getResource().getResourceName(),
            event.getResource().getRepositoryUrl());
    }

    @Override
    public void transferFailed(TransferEvent event) {
        LOG.warn("Transfer failed: {} from {}",
            event.getResource().getResourceName(),
            event.getResource().getRepositoryUrl());
    }

    @Override
    public void transferCorrupted(TransferEvent event) {
        LOG.warn("Transfer corrupted: {} from {}",
            event.getResource().getResourceName(),
            event.getResource().getRepositoryUrl());
    }
}
