package com.therighthandapp.agentmesh.vcs;

/**
 * Generic VCS repository representation
 */
public class VcsRepository {
    private String id;
    private String name;
    private String fullName;
    private String defaultBranch;
    private String url;
    private String owner;

    public VcsRepository() {}

    public VcsRepository(String id, String name, String fullName, String defaultBranch) {
        this.id = id;
        this.name = name;
        this.fullName = fullName;
        this.defaultBranch = defaultBranch;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}

