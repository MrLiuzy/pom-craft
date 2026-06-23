package com.github.mrliuzy.pomcraft.model;

import java.util.Set;

public class ConflictInfo {

    private String groupId;
    private String artifactId;
    private String version;
    private Set<String> conflictingVersions;

    public ConflictInfo() {}

    public ConflictInfo(String groupId, String artifactId, String version, Set<String> conflictingVersions) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.conflictingVersions = conflictingVersions;
    }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public Set<String> getConflictingVersions() { return conflictingVersions; }
    public void setConflictingVersions(Set<String> conflictingVersions) {
        this.conflictingVersions = conflictingVersions;
    }
}
