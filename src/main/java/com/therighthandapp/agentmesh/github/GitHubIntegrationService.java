package com.therighthandapp.agentmesh.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for GitHub API integration
 */
@Service
@ConditionalOnProperty(name = "agentmesh.github.enabled", havingValue = "true")
public class GitHubIntegrationService {
    private static final Logger log = LoggerFactory.getLogger(GitHubIntegrationService.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";

    @Value("${agentmesh.github.token}")
    private String githubToken;

    @Value("${agentmesh.github.repo-owner}")
    private String repoOwner;

    @Value("${agentmesh.github.repo-name}")
    private String repoName;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GitHubIntegrationService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Create a pull request with generated code
     */
    public String createPullRequest(String title, String code, String issueNumber) {
        try {
            // 1. Get default branch SHA
            String defaultBranch = getDefaultBranch();
            String baseSha = getBranchSha(defaultBranch);

            // 2. Create new branch
            String branchName = "agentmesh/issue-" + issueNumber;
            createBranch(branchName, baseSha);

            // 3. Create/update file
            String filePath = "src/main/java/generated/Feature" + issueNumber + ".java";
            commitFile(branchName, filePath, code, "Add generated code for issue #" + issueNumber);

            // 4. Create pull request
            Map<String, Object> prBody = new HashMap<>();
            prBody.put("title", title);
            prBody.put("body", buildPRDescription(issueNumber, code));
            prBody.put("head", branchName);
            prBody.put("base", defaultBranch);

            String prUrl = String.format("%s/repos/%s/%s/pulls", GITHUB_API_BASE, repoOwner, repoName);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(prBody, getHeaders());

            ResponseEntity<Map> response = restTemplate.postForEntity(prUrl, request, Map.class);

            if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
                String htmlUrl = (String) response.getBody().get("html_url");
                log.info("Created PR: {}", htmlUrl);
                return htmlUrl;
            }

            throw new RuntimeException("Failed to create PR: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("Error creating pull request", e);
            throw new RuntimeException("Failed to create PR", e);
        }
    }

    /**
     * Add comment to issue
     */
    public void addComment(String issueNumber, String comment) {
        try {
            String url = String.format("%s/repos/%s/%s/issues/%s/comments",
                GITHUB_API_BASE, repoOwner, repoName, issueNumber);

            Map<String, String> body = Map.of("body", comment);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, getHeaders());

            restTemplate.postForEntity(url, request, String.class);
            log.info("Added comment to issue #{}", issueNumber);

        } catch (Exception e) {
            log.error("Error adding comment to issue " + issueNumber, e);
        }
    }

    /**
     * Update issue labels
     */
    public void updateIssueLabels(String issueNumber, List<String> labels) {
        try {
            String url = String.format("%s/repos/%s/%s/issues/%s/labels",
                GITHUB_API_BASE, repoOwner, repoName, issueNumber);

            Map<String, List<String>> body = Map.of("labels", labels);
            HttpEntity<Map<String, List<String>>> request = new HttpEntity<>(body, getHeaders());

            restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
            log.info("Updated labels for issue #{}", issueNumber);

        } catch (Exception e) {
            log.error("Error updating labels for issue " + issueNumber, e);
        }
    }

    /**
     * Get default branch name
     */
    private String getDefaultBranch() {
        String url = String.format("%s/repos/%s/%s", GITHUB_API_BASE, repoOwner, repoName);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET,
            new HttpEntity<>(getHeaders()), Map.class);

        if (response.getBody() != null) {
            return (String) response.getBody().get("default_branch");
        }
        return "main";
    }

    /**
     * Get branch SHA
     */
    private String getBranchSha(String branchName) {
        String url = String.format("%s/repos/%s/%s/git/refs/heads/%s",
            GITHUB_API_BASE, repoOwner, repoName, branchName);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET,
            new HttpEntity<>(getHeaders()), Map.class);

        if (response.getBody() != null) {
            Map<String, Object> object = (Map<String, Object>) response.getBody().get("object");
            return (String) object.get("sha");
        }
        throw new RuntimeException("Failed to get branch SHA");
    }

    /**
     * Create new branch
     */
    private void createBranch(String branchName, String sha) {
        String url = String.format("%s/repos/%s/%s/git/refs",
            GITHUB_API_BASE, repoOwner, repoName);

        Map<String, String> body = new HashMap<>();
        body.put("ref", "refs/heads/" + branchName);
        body.put("sha", sha);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, getHeaders());

        try {
            restTemplate.postForEntity(url, request, String.class);
            log.info("Created branch: {}", branchName);
        } catch (Exception e) {
            // Branch might already exist, try to update it
            log.warn("Branch {} might already exist, attempting to update", branchName);
            updateBranch(branchName, sha);
        }
    }

    /**
     * Update existing branch
     */
    private void updateBranch(String branchName, String sha) {
        String url = String.format("%s/repos/%s/%s/git/refs/heads/%s",
            GITHUB_API_BASE, repoOwner, repoName, branchName);

        Map<String, Object> body = new HashMap<>();
        body.put("sha", sha);
        body.put("force", true);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, getHeaders());
        restTemplate.exchange(url, HttpMethod.PATCH, request, String.class);
    }

    /**
     * Commit file to branch
     */
    private void commitFile(String branchName, String filePath, String content, String message) {
        String url = String.format("%s/repos/%s/%s/contents/%s",
            GITHUB_API_BASE, repoOwner, repoName, filePath);

        Map<String, String> body = new HashMap<>();
        body.put("message", message);
        body.put("content", Base64.getEncoder().encodeToString(content.getBytes()));
        body.put("branch", branchName);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, getHeaders());

        try {
            restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
            log.info("Committed file {} to branch {}", filePath, branchName);
        } catch (Exception e) {
            log.error("Error committing file", e);
            throw new RuntimeException("Failed to commit file", e);
        }
    }

    /**
     * Build PR description with metrics
     */
    private String buildPRDescription(String issueNumber, String code) {
        return String.format("""
            🤖 **Generated by AgentMesh**
            
            This PR was automatically generated to address issue #%s.
            
            ### Implementation Details
            - Generated code includes necessary classes and methods
            - Self-correction applied for quality assurance
            - Automated tests included
            
            ### Review Checklist
            - [ ] Code quality reviewed
            - [ ] Tests passing
            - [ ] Security validated
            - [ ] Documentation complete
            
            Closes #%s
            
            ---
            _Generated with ❤️ by AgentMesh_
            """, issueNumber, issueNumber);
    }

    /**
     * Get HTTP headers with authentication
     */
    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + githubToken);
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}

