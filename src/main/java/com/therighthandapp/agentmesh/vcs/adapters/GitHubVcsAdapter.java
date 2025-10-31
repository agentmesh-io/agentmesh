package com.therighthandapp.agentmesh.vcs.adapters;

import com.therighthandapp.agentmesh.github.GitHubIntegrationService;
import com.therighthandapp.agentmesh.vcs.VcsIssue;
import com.therighthandapp.agentmesh.vcs.VcsProvider;
import com.therighthandapp.agentmesh.vcs.VcsRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * GitHub adapter implementing VcsProvider interface
 */
@Component("githubVcsProvider")
@ConditionalOnBean(GitHubIntegrationService.class)
@ConditionalOnProperty(name = "agentmesh.github.enabled", havingValue = "true")
public class GitHubVcsAdapter implements VcsProvider {

    private final GitHubIntegrationService githubService;

    public GitHubVcsAdapter(GitHubIntegrationService githubService) {
        this.githubService = githubService;
    }

    @Override
    public String getProviderName() {
        return "github";
    }

    @Override
    public String createPullRequest(String title, String description, String sourceBranch,
                                    String targetBranch, Map<String, String> files) {
        // Extract issue number from description if present
        String issueNumber = extractIssueNumber(description);

        // Use the first file's content for now (simplified)
        // In production, would iterate and commit all files
        String code = files.values().iterator().next();

        return githubService.createPullRequest(title, code, issueNumber);
    }

    @Override
    public void addComment(String issueId, String comment) {
        githubService.addComment(issueId, comment);
    }

    @Override
    public void updateLabels(String issueId, List<String> labels) {
        githubService.updateIssueLabels(issueId, labels);
    }

    @Override
    public VcsIssue getIssue(String issueId) {
        // TODO: Implement get issue from GitHub API
        // For now, return basic issue
        VcsIssue issue = new VcsIssue();
        issue.setId(issueId);
        issue.setNumber(issueId);
        return issue;
    }

    @Override
    public VcsRepository getRepository() {
        // TODO: Implement get repository details
        VcsRepository repo = new VcsRepository();
        repo.setDefaultBranch("main");
        return repo;
    }

    @Override
    public void createBranch(String branchName, String fromBranch) {
        // Handled internally by GitHubIntegrationService.createPullRequest
    }

    @Override
    public void commitFiles(String branchName, String commitMessage, Map<String, String> files) {
        // Handled internally by GitHubIntegrationService.createPullRequest
    }

    private String extractIssueNumber(String description) {
        // Extract issue number from "Closes #123" pattern
        if (description != null && description.contains("Closes #")) {
            int start = description.indexOf("Closes #") + 8;
            int end = description.indexOf(" ", start);
            if (end == -1) end = description.indexOf("\n", start);
            if (end == -1) end = description.length();
            return description.substring(start, end);
        }
        return "0";
    }
}

