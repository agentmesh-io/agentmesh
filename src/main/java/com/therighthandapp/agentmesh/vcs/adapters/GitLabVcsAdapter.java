package com.therighthandapp.agentmesh.vcs.adapters;

import com.therighthandapp.agentmesh.vcs.VcsIssue;
import com.therighthandapp.agentmesh.vcs.VcsProvider;
import com.therighthandapp.agentmesh.vcs.VcsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GitLab adapter implementing VcsProvider interface
 *
 * Configuration:
 * agentmesh.vcs.provider=gitlab
 * agentmesh.gitlab.url=https://gitlab.com
 * agentmesh.gitlab.token=${GITLAB_TOKEN}
 * agentmesh.gitlab.project-id=your-project-id
 */
@Component("gitlabVcsProvider")
@ConditionalOnProperty(name = "agentmesh.vcs.provider", havingValue = "gitlab")
public class GitLabVcsAdapter implements VcsProvider {
    private static final Logger log = LoggerFactory.getLogger(GitLabVcsAdapter.class);

    @Value("${agentmesh.gitlab.url:https://gitlab.com}")
    private String gitlabUrl;

    @Value("${agentmesh.gitlab.token}")
    private String gitlabToken;

    @Value("${agentmesh.gitlab.project-id}")
    private String projectId;

    private final RestTemplate restTemplate;

    public GitLabVcsAdapter() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String getProviderName() {
        return "gitlab";
    }

    @Override
    public String createPullRequest(String title, String description, String sourceBranch,
                                    String targetBranch, Map<String, String> files) {
        log.info("Creating GitLab merge request: {}", title);

        // 1. Create branch
        createBranch(sourceBranch, targetBranch);

        // 2. Commit files
        commitFiles(sourceBranch, "Add generated code", files);

        // 3. Create merge request
        String url = String.format("%s/api/v4/projects/%s/merge_requests",
            gitlabUrl, projectId);

        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", description);
        body.put("source_branch", sourceBranch);
        body.put("target_branch", targetBranch);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, getHeaders());
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        if (response.getBody() != null) {
            return (String) response.getBody().get("web_url");
        }

        return null;
    }

    @Override
    public void addComment(String issueId, String comment) {
        log.info("Adding comment to GitLab issue #{}", issueId);

        String url = String.format("%s/api/v4/projects/%s/issues/%s/notes",
            gitlabUrl, projectId, issueId);

        Map<String, String> body = Map.of("body", comment);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, getHeaders());

        restTemplate.postForEntity(url, request, String.class);
    }

    @Override
    public void updateLabels(String issueId, List<String> labels) {
        log.info("Updating labels for GitLab issue #{}", issueId);

        String url = String.format("%s/api/v4/projects/%s/issues/%s",
            gitlabUrl, projectId, issueId);

        Map<String, String> body = Map.of("labels", String.join(",", labels));
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, getHeaders());

        restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
    }

    @Override
    public VcsIssue getIssue(String issueId) {
        String url = String.format("%s/api/v4/projects/%s/issues/%s",
            gitlabUrl, projectId, issueId);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET,
            new HttpEntity<>(getHeaders()), Map.class);

        if (response.getBody() != null) {
            Map<String, Object> data = response.getBody();
            VcsIssue issue = new VcsIssue();
            issue.setId(String.valueOf(data.get("id")));
            issue.setNumber(String.valueOf(data.get("iid")));
            issue.setTitle((String) data.get("title"));
            issue.setDescription((String) data.get("description"));
            return issue;
        }

        return null;
    }

    @Override
    public VcsRepository getRepository() {
        String url = String.format("%s/api/v4/projects/%s", gitlabUrl, projectId);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET,
            new HttpEntity<>(getHeaders()), Map.class);

        if (response.getBody() != null) {
            Map<String, Object> data = response.getBody();
            VcsRepository repo = new VcsRepository();
            repo.setId(String.valueOf(data.get("id")));
            repo.setName((String) data.get("name"));
            repo.setFullName((String) data.get("path_with_namespace"));
            repo.setDefaultBranch((String) data.get("default_branch"));
            return repo;
        }

        return null;
    }

    @Override
    public void createBranch(String branchName, String fromBranch) {
        log.info("Creating GitLab branch: {}", branchName);

        String url = String.format("%s/api/v4/projects/%s/repository/branches",
            gitlabUrl, projectId);

        Map<String, String> body = Map.of(
            "branch", branchName,
            "ref", fromBranch
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, getHeaders());
        restTemplate.postForEntity(url, request, String.class);
    }

    @Override
    public void commitFiles(String branchName, String commitMessage, Map<String, String> files) {
        log.info("Committing {} files to branch {}", files.size(), branchName);

        String url = String.format("%s/api/v4/projects/%s/repository/commits",
            gitlabUrl, projectId);

        // Build actions array
        List<Map<String, String>> actions = files.entrySet().stream()
            .map(entry -> Map.of(
                "action", "create",
                "file_path", entry.getKey(),
                "content", entry.getValue()
            ))
            .toList();

        Map<String, Object> body = new HashMap<>();
        body.put("branch", branchName);
        body.put("commit_message", commitMessage);
        body.put("actions", actions);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, getHeaders());
        restTemplate.postForEntity(url, request, String.class);
    }

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("PRIVATE-TOKEN", gitlabToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}

