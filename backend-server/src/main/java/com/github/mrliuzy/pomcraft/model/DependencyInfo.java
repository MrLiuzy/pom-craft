package com.github.mrliuzy.pomcraft.model;

import java.util.ArrayList;
import java.util.List;

public class DependencyInfo {

    private String groupId;
    private String artifactId;
    private String version;
    private String scope;
    private boolean optional;
    private String type;
    private String classifier;
    private boolean resolved = true;
    private String errorMessage;
    private String managedVersion;
    private List<DependencyInfo> children = new ArrayList<>();

    public DependencyInfo() {}

    public DependencyInfo(String groupId, String artifactId, String version,
                          String scope, boolean optional, String type, String classifier) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.scope = scope;
        this.optional = optional;
        this.type = type;
        this.classifier = classifier;
    }

    public String gaKey() {
        return groupId + ":" + artifactId;
    }

    public String gav() {
        return groupId + ":" + artifactId + ":" + version;
    }

    // --- getters / setters ---

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public boolean isOptional() { return optional; }
    public void setOptional(boolean optional) { this.optional = optional; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getClassifier() { return classifier; }
    public void setClassifier(String classifier) { this.classifier = classifier; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getManagedVersion() { return managedVersion; }
    public void setManagedVersion(String managedVersion) { this.managedVersion = managedVersion; }

    public List<DependencyInfo> getChildren() { return children; }
    public void setChildren(List<DependencyInfo> children) { this.children = children; }

    @Override
    public String toString() {
        return gav();
    }
}
