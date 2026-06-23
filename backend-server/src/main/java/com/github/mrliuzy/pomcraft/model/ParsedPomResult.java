package com.github.mrliuzy.pomcraft.model;

import java.util.ArrayList;
import java.util.List;

public class ParsedPomResult {

    private boolean success;
    private String errorMessage;
    private List<DependencyInfo> directDependencies = new ArrayList<>();
    private List<DependencyInfo> allDependencies = new ArrayList<>();
    private List<ConflictInfo> conflicts = new ArrayList<>();

    public ParsedPomResult() {}

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public List<DependencyInfo> getDirectDependencies() { return directDependencies; }
    public void setDirectDependencies(List<DependencyInfo> directDependencies) {
        this.directDependencies = directDependencies;
    }

    public List<DependencyInfo> getAllDependencies() { return allDependencies; }
    public void setAllDependencies(List<DependencyInfo> allDependencies) {
        this.allDependencies = allDependencies;
    }

    public List<ConflictInfo> getConflicts() { return conflicts; }
    public void setConflicts(List<ConflictInfo> conflicts) { this.conflicts = conflicts; }
}
