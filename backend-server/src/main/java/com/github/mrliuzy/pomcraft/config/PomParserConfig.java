package com.github.mrliuzy.pomcraft.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PomParserConfig {

    private File targetPom;
    private boolean offline;
    private boolean skipFailedResolution;
    private File settingsXml;
    private List<File> workspaceDirectories = new ArrayList<>();

    public PomParserConfig() {}

    public PomParserConfig(File targetPom) {
        this.targetPom = targetPom;
    }

    public File getTargetPom() { return targetPom; }
    public void setTargetPom(File targetPom) { this.targetPom = targetPom; }

    public boolean isOffline() { return offline; }
    public void setOffline(boolean offline) { this.offline = offline; }

    public boolean isSkipFailedResolution() { return skipFailedResolution; }
    public void setSkipFailedResolution(boolean skipFailedResolution) {
        this.skipFailedResolution = skipFailedResolution;
    }

    public File getSettingsXml() { return settingsXml; }
    public void setSettingsXml(File settingsXml) { this.settingsXml = settingsXml; }

    public List<File> getWorkspaceDirectories() { return workspaceDirectories; }
    public void setWorkspaceDirectories(List<File> workspaceDirectories) {
        this.workspaceDirectories = workspaceDirectories;
    }

    public void validate() {
        if (targetPom == null) {
            throw new IllegalArgumentException("targetPom must not be null");
        }
        if (!targetPom.isFile()) {
            throw new IllegalArgumentException("targetPom does not exist or is not a file: "
                + targetPom.getAbsolutePath());
        }
    }
}
