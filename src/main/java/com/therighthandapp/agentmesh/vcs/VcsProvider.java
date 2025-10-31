package com.therighthandapp.agentmesh.vcs;

import java.util.List;

/**
 * Interface for Version Control System integration
 * Supports GitHub, GitLab, Bitbucket, Azure DevOps, etc.
 */
public interface VcsProvider {

    /**
     * Get the provider name (github, gitlab, bitbucket, etc.)
     */
    String getProviderName();

    /**
     * Create a pull/merge request with generated code
     *
     * @param title PR title
     * @param description PR description
     * @param sourceBranch Source branch name
     * @param targetBranch Target branch (usually main/master)
     * @param files Files to commit (path -> content)
     * @return PR/MR URL
     */
    String createPullRequest(String title, String description, String sourceBranch,
                            String targetBranch, java.util.Map<String, String> files);

    /**
     * Add comment to an issue/ticket
     */
    void addComment(String issueId, String comment);

    /**
     * Update labels/tags on an issue
     */
    void updateLabels(String issueId, List<String> labels);

    /**
     * Get issue details
     */
    VcsIssue getIssue(String issueId);

    /**
     * Get repository information
     */
    VcsRepository getRepository();

    /**
     * Create a branch
     */
    void createBranch(String branchName, String fromBranch);

    /**
     * Commit files to a branch
     */
    void commitFiles(String branchName, String commitMessage,
                     java.util.Map<String, String> files);
}

