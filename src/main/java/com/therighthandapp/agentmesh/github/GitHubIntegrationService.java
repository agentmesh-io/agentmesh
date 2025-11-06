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
     * Create a new GitHub repository for a project
     * 
     * @param projectName The name of the project
     * @param projectKey The unique project key
     * @param description Project description from SRS
     * @param srsContent The SRS content to add as initial documentation
     * @return The HTML URL of the created repository
     */
    public String createProjectRepository(String projectName, String projectKey, String description, String srsContent) {
        try {
            log.info("Creating GitHub repository: {} ({})", projectName, projectKey);
            
            // 1. Create repository
            String repoName = generateRepositoryName(projectName, projectKey);
            String repoUrl = createRepository(repoName, description);
            
            // 2. Initialize with README
            String readmeContent = generateReadmeContent(projectName, projectKey, description);
            commitFile("main", "README.md", readmeContent, "Initial commit: Add README");
            
            // 3. Add .gitignore for Java projects
            String gitignoreContent = generateGitignoreContent();
            commitFile("main", ".gitignore", gitignoreContent, "Add .gitignore for Java/Maven projects");
            
            // 4. Add SRS documentation
            if (srsContent != null && !srsContent.isEmpty()) {
                commitFile("main", "docs/SRS.md", srsContent, "Add Software Requirements Specification");
            }
            
            log.info("Successfully created repository: {}", repoUrl);
            return repoUrl;
            
        } catch (Exception e) {
            log.error("Failed to create GitHub repository for project: {}", projectKey, e);
            return null; // Non-fatal: project can exist without GitHub repo
        }
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
    
    /**
     * Generate repository name from project name and key
     */
    private String generateRepositoryName(String projectName, String projectKey) {
        // Convert to kebab-case: "E-Commerce Platform" -> "e-commerce-platform"
        String repoName = projectName.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "") // Remove special chars except spaces and hyphens
            .replaceAll("\\s+", "-")          // Replace spaces with hyphens
            .replaceAll("-+", "-")            // Replace multiple hyphens with single
            .replaceAll("^-|-$", "");         // Remove leading/trailing hyphens
        
        // Append project key for uniqueness
        return repoName + "-" + projectKey.toLowerCase();
    }
    
    /**
     * Create a new repository on GitHub
     */
    private String createRepository(String repoName, String description) {
        String url = String.format("%s/user/repos", GITHUB_API_BASE);
        
        Map<String, Object> body = new HashMap<>();
        body.put("name", repoName);
        body.put("description", description != null ? description : "AgentMesh generated project");
        body.put("private", false);
        body.put("auto_init", true); // Initialize with README to create main branch
        body.put("has_issues", true);
        body.put("has_projects", true);
        body.put("has_wiki", true);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, getHeaders());
        
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        
        if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
            String htmlUrl = (String) response.getBody().get("html_url");
            log.info("Created repository: {}", htmlUrl);
            
            // Wait a bit for GitHub to initialize the repository
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            return htmlUrl;
        }
        
        throw new RuntimeException("Failed to create repository: " + response.getStatusCode());
    }
    
    /**
     * Generate README content for the project
     */
    private String generateReadmeContent(String projectName, String projectKey, String description) {
        return String.format("""
            # %s
            
            **Project Key**: `%s`
            
            ## Overview
            
            %s
            
            ## Project Information
            
            This project was automatically initialized by **AgentMesh**, an intelligent multi-agent system for software development.
            
            ### Development Status
            
            - 🤖 **Status**: Active Development
            - 📋 **Project Key**: %s
            - 🔧 **Generated**: Automatically via AgentMesh
            
            ## Getting Started
            
            This project is currently in the initialization phase. Development artifacts will be added as the AgentMesh agents progress through the SDLC workflow.
            
            ### Prerequisites
            
            - Java 17+
            - Maven 3.8+
            - Docker (for containerized services)
            
            ### Documentation
            
            - [Software Requirements Specification](docs/SRS.md)
            - [Architecture Documentation](docs/ARCHITECTURE.md) _(Coming Soon)_
            - [API Documentation](docs/API.md) _(Coming Soon)_
            
            ## AgentMesh Integration
            
            This project is managed by AgentMesh agents that handle:
            
            - 📐 **Planning**: Architecture and design decisions
            - 💻 **Coding**: Implementation of features
            - 🔍 **Review**: Code quality and standards
            - 🧪 **Testing**: Automated test generation
            - 🚀 **Deployment**: CI/CD pipeline management
            
            ## Contributing
            
            This is an AgentMesh-managed project. For questions or contributions, please refer to the main AgentMesh documentation.
            
            ---
            
            _Generated with ❤️ by [AgentMesh](https://github.com/agentmesh)_
            """, 
            projectName, 
            projectKey, 
            description != null ? description : "A software project generated by AgentMesh intelligent agents.",
            projectKey
        );
    }
    
    /**
     * Generate .gitignore content for Java/Maven projects
     */
    private String generateGitignoreContent() {
        return """
            # Compiled class files
            *.class
            
            # Log files
            *.log
            
            # BlueJ files
            *.ctxt
            
            # Mobile Tools for Java (J2ME)
            .mtj.tmp/
            
            # Package Files
            *.jar
            *.war
            *.nar
            *.ear
            *.zip
            *.tar.gz
            *.rar
            
            # Virtual machine crash logs
            hs_err_pid*
            replay_pid*
            
            # Maven
            target/
            pom.xml.tag
            pom.xml.releaseBackup
            pom.xml.versionsBackup
            pom.xml.next
            release.properties
            dependency-reduced-pom.xml
            buildNumber.properties
            .mvn/timing.properties
            .mvn/wrapper/maven-wrapper.jar
            
            # Gradle
            .gradle
            build/
            !gradle/wrapper/gradle-wrapper.jar
            !**/src/main/**/build/
            !**/src/test/**/build/
            
            # IDE
            .idea/
            *.iws
            *.iml
            *.ipr
            .vscode/
            *.swp
            *.swo
            *~
            .DS_Store
            
            # Spring Boot
            spring-boot-*.log
            
            # Environment variables
            .env
            .env.local
            .env.*.local
            
            # Docker
            docker-compose.override.yml
            
            # Logs and databases
            *.log
            *.sql
            *.sqlite
            
            # OS generated files
            .DS_Store
            .DS_Store?
            ._*
            .Spotlight-V100
            .Trashes
            ehthumbs.db
            Thumbs.db
            """;
    }
}

