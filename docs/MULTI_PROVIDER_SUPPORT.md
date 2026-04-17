# Multi-Provider VCS and Project Management Support

**Status:** ✅ IMPLEMENTED  
**Last Updated:** October 31, 2025  
**Architecture:** Plugin-based adapter pattern

---

## 📊 Overview

AgentMesh now supports **multiple Version Control Systems (VCS)** and **Project Management tools** through a plugin-based adapter architecture. The system uses interface-based abstraction to allow seamless integration with different providers.

### Supported Providers

#### Version Control Systems (VCS)
- ✅ **GitHub** - Full implementation
- ✅ **GitLab** - Complete adapter with API integration
- 🔲 **Bitbucket** - Ready to implement (interface defined)
- 🔲 **Azure DevOps** - Ready to implement (interface defined)
- 🔲 **Gitea** - Ready to implement (interface defined)

#### Project Management Tools
- ✅ **GitHub Projects** - Full implementation
- ✅ **Jira** - Complete adapter with API integration
- 🔲 **Azure Boards** - Ready to implement (interface defined)
- 🔲 **Linear** - Ready to implement (interface defined)
- 🔲 **Asana** - Ready to implement (interface defined)
- 🔲 **Trello** - Ready to implement (interface defined)

---

## 🏗️ Architecture

### Core Interfaces

```
┌──────────────────────────────────────────────────────────────┐
│                    AgentMesh Core                             │
│  (Blackboard, Agents, Self-Correction, MAST)                 │
└────────────────┬────────────────────┬────────────────────────┘
                 │                    │
        ┌────────▼────────┐  ┌────────▼────────────┐
        │  VcsProvider    │  │ ProjectManagement   │
        │   Interface     │  │   Provider Interface │
        └────────┬────────┘  └────────┬─────────────┘
                 │                    │
     ┌───────────┼────────────┐       ┼────────────────┐
     │           │            │       │                │
┌────▼───┐ ┌────▼────┐ ┌─────▼──┐ ┌──▼──────┐ ┌──────▼────┐
│ GitHub │ │ GitLab  │ │Bitbucket│ │ GitHub  │ │   Jira    │
│Adapter │ │ Adapter │ │ Adapter │ │Projects │ │  Adapter  │
└────────┘ └─────────┘ └─────────┘ └─────────┘ └───────────┘
```

### VcsProvider Interface

```java
public interface VcsProvider {
    String getProviderName();
    
    // PR/MR Management
    String createPullRequest(String title, String description, 
                            String sourceBranch, String targetBranch,
                            Map<String, String> files);
    
    // Issue Management
    void addComment(String issueId, String comment);
    void updateLabels(String issueId, List<String> labels);
    VcsIssue getIssue(String issueId);
    
    // Repository Operations
    VcsRepository getRepository();
    void createBranch(String branchName, String fromBranch);
    void commitFiles(String branchName, String commitMessage,
                    Map<String, String> files);
}
```

### ProjectManagementProvider Interface

```java
public interface ProjectManagementProvider {
    String getProviderName();
    
    // Project Board Management
    String addIssueToProject(String issueId);
    void updateProjectItem(String itemId, String status,
                          Map<String, Object> customFields);
    
    // Metrics & Reporting
    void addCompletionMetrics(String itemId, int iterations,
                             boolean success, String failureReason);
    
    // Queries
    ProjectItem getProjectItem(String itemId);
    List<ProjectItem> getProjectItemsByStatus(String status);
    void updateCustomField(String itemId, String fieldName, Object value);
}
```

---

## 📝 Configuration

### Option 1: GitHub + GitHub Projects (Default)

```yaml
agentmesh:
  # VCS Provider
  vcs:
    provider: github  # or gitlab, bitbucket, azure-devops
  
  # GitHub Configuration
  github:
    enabled: true
    token: ${GITHUB_TOKEN}
    repo-owner: your-org
    repo-name: your-repo
  
  # Project Management Provider
  projectmanagement:
    provider: github-projects  # or jira, azure-boards, linear
  
  # GitHub Projects Configuration
  github:
    projects:
      enabled: true
      project-id: ${GITHUB_PROJECT_ID}
```

### Option 2: GitLab + Jira

```yaml
agentmesh:
  # VCS Provider
  vcs:
    provider: gitlab
  
  # GitLab Configuration
  gitlab:
    url: https://gitlab.com  # or self-hosted URL
    token: ${GITLAB_TOKEN}
    project-id: your-project-id
  
  # Project Management Provider
  projectmanagement:
    provider: jira
  
  # Jira Configuration
  jira:
    url: https://your-domain.atlassian.net
    username: ${JIRA_USERNAME}
    api-token: ${JIRA_API_TOKEN}
    project-key: PROJ
```

### Option 3: GitHub + Jira (Hybrid)

```yaml
agentmesh:
  # VCS: GitHub for code
  vcs:
    provider: github
  
  github:
    enabled: true
    token: ${GITHUB_TOKEN}
    repo-owner: your-org
    repo-name: your-repo
  
  # PM: Jira for project management
  projectmanagement:
    provider: jira
  
  jira:
    url: https://your-domain.atlassian.net
    username: ${JIRA_USERNAME}
    api-token: ${JIRA_API_TOKEN}
    project-key: PROJ
```

---

## 🚀 Usage Examples

### Example 1: GitHub Workflow

```bash
# Developer creates GitHub issue
gh issue create \
  --title "Add Payment API" \
  --body "Implement Stripe integration" \
  --label "agentmesh"

# AgentMesh automatically:
# 1. Detects issue via webhook (GitHub)
# 2. Adds to project board (GitHub Projects)
# 3. Generates code with self-correction
# 4. Creates PR (GitHub)
# 5. Updates project status (GitHub Projects)
```

### Example 2: GitLab + Jira Workflow

```bash
# Developer creates Jira ticket: PROJ-123

# Configure webhook to trigger AgentMesh:
# Jira → Webhook → AgentMesh

# AgentMesh automatically:
# 1. Receives Jira webhook
# 2. Updates Jira ticket status to "In Progress"
# 3. Generates code with self-correction
# 4. Creates GitLab merge request
# 5. Adds comment to Jira with MR link
# 6. Updates Jira custom fields (iterations, violations)
# 7. Transitions ticket to "Code Review"
```

### Example 3: GitHub + Jira (Hybrid)

```bash
# PM creates Jira epic: PROJ-100
# Developer creates GitHub issue: #45 (references PROJ-100)

# AgentMesh workflow:
# 1. GitHub webhook triggers on issue creation
# 2. Links to Jira ticket automatically
# 3. Updates both Jira and GitHub
# 4. Creates PR on GitHub
# 5. Updates Jira with PR link
# 6. Both teams stay synchronized
```

---

## 🔌 Implementing New Providers

### Step 1: Create VCS Adapter

```java
@Component("bitbucketVcsProvider")
@ConditionalOnProperty(name = "agentmesh.vcs.provider", havingValue = "bitbucket")
public class BitbucketVcsAdapter implements VcsProvider {
    
    @Value("${agentmesh.bitbucket.workspace}")
    private String workspace;
    
    @Value("${agentmesh.bitbucket.repo-slug}")
    private String repoSlug;
    
    @Value("${agentmesh.bitbucket.token}")
    private String token;
    
    @Override
    public String getProviderName() {
        return "bitbucket";
    }
    
    @Override
    public String createPullRequest(String title, String description,
                                    String sourceBranch, String targetBranch,
                                    Map<String, String> files) {
        // Bitbucket REST API implementation
        String url = String.format(
            "https://api.bitbucket.org/2.0/repositories/%s/%s/pullrequests",
            workspace, repoSlug
        );
        
        // ... implementation ...
        
        return prUrl;
    }
    
    // ... implement other methods ...
}
```

### Step 2: Create PM Adapter

```java
@Component("linearProjectManagementProvider")
@ConditionalOnProperty(name = "agentmesh.projectmanagement.provider", 
                      havingValue = "linear")
public class LinearAdapter implements ProjectManagementProvider {
    
    @Value("${agentmesh.linear.api-key}")
    private String apiKey;
    
    @Value("${agentmesh.linear.team-id}")
    private String teamId;
    
    @Override
    public String getProviderName() {
        return "linear";
    }
    
    @Override
    public String addIssueToProject(String issueId) {
        // Linear GraphQL API implementation
        // ... implementation ...
    }
    
    // ... implement other methods ...
}
```

### Step 3: Add Configuration

```yaml
agentmesh:
  vcs:
    provider: bitbucket
  
  bitbucket:
    workspace: your-workspace
    repo-slug: your-repo
    token: ${BITBUCKET_TOKEN}
  
  projectmanagement:
    provider: linear
  
  linear:
    api-key: ${LINEAR_API_KEY}
    team-id: your-team-id
```

### Step 4: Test

```bash
# Set environment variables
export BITBUCKET_TOKEN=your_token
export LINEAR_API_KEY=your_key

# Start AgentMesh
mvn spring-boot:run

# Create test issue and verify integration
```

---

## 📊 Provider Comparison

### VCS Providers

| Feature | GitHub | GitLab | Bitbucket | Azure DevOps |
|---------|--------|--------|-----------|--------------|
| **PR/MR Creation** | ✅ | ✅ | 🔲 | 🔲 |
| **Comments** | ✅ | ✅ | 🔲 | 🔲 |
| **Labels/Tags** | ✅ | ✅ | 🔲 | 🔲 |
| **Branch Management** | ✅ | ✅ | 🔲 | 🔲 |
| **Webhooks** | ✅ | ✅ | 🔲 | 🔲 |
| **Self-Hosted** | ❌ | ✅ | ✅ | ✅ |

### PM Providers

| Feature | GitHub Projects | Jira | Azure Boards | Linear |
|---------|----------------|------|--------------|--------|
| **Board Management** | ✅ | ✅ | 🔲 | 🔲 |
| **Custom Fields** | ✅ | ✅ | 🔲 | 🔲 |
| **Status Updates** | ✅ | ✅ | 🔲 | 🔲 |
| **Metrics** | ✅ | ✅ | 🔲 | 🔲 |
| **GraphQL API** | ✅ | ❌ | ❌ | ✅ |
| **Self-Hosted** | ❌ | ✅ | ✅ | ❌ |

---

## 🔧 Advanced Configuration

### Multiple Repositories

```yaml
# Support multiple repos by deploying multiple instances
# Instance 1: Product Repo
agentmesh:
  vcs:
    provider: github
  github:
    repo-owner: acme-corp
    repo-name: product-api

# Instance 2: Frontend Repo
agentmesh:
  vcs:
    provider: github
  github:
    repo-owner: acme-corp
    repo-name: product-frontend
```

### Fallback Configuration

```yaml
agentmesh:
  vcs:
    provider: github
    fallback-provider: gitlab  # Use GitLab if GitHub unavailable
  
  projectmanagement:
    provider: jira
    fallback-provider: github-projects
```

### Provider-Specific Settings

```yaml
agentmesh:
  gitlab:
    timeout: 30000  # 30 seconds
    max-retries: 3
    merge-method: squash  # squash, merge, rebase
  
  jira:
    timeout: 60000
    default-issue-type: Story
    auto-transition: true
```

---

## 🧪 Testing Multi-Provider Setup

### Test GitHub + GitHub Projects

```bash
export GITHUB_TOKEN=ghp_...
export GITHUB_PROJECT_ID=PVT_...

mvn spring-boot:run

gh issue create --title "Test" --label "agentmesh"
```

### Test GitLab + Jira

```bash
export GITLAB_TOKEN=glpat-...
export JIRA_API_TOKEN=...

mvn spring-boot:run -Dspring.profiles.active=gitlab-jira

# Create Jira ticket and verify GitLab MR creation
```

### Test Hybrid (GitHub + Jira)

```bash
export GITHUB_TOKEN=ghp_...
export JIRA_API_TOKEN=...

mvn spring-boot:run -Dspring.profiles.active=hybrid

# Create GitHub issue
# Verify Jira ticket is updated
```

---

## 📈 Migration Guide

### Migrating from GitHub to GitLab

```bash
# 1. Update configuration
# Change: agentmesh.vcs.provider=github
# To: agentmesh.vcs.provider=gitlab

# 2. Add GitLab credentials
export GITLAB_TOKEN=glpat-...
export GITLAB_PROJECT_ID=12345

# 3. Restart AgentMesh
mvn spring-boot:run

# 4. Update webhooks in GitLab
# Settings → Webhooks → Add webhook
# URL: https://your-server.com/api/gitlab/webhook
```

### Migrating from GitHub Projects to Jira

```bash
# 1. Update configuration
# Change: agentmesh.projectmanagement.provider=github-projects
# To: agentmesh.projectmanagement.provider=jira

# 2. Add Jira credentials
export JIRA_USERNAME=your-email@example.com
export JIRA_API_TOKEN=...
export JIRA_PROJECT_KEY=PROJ

# 3. Restart AgentMesh
mvn spring-boot:run

# 4. Historical data migration (manual)
# Export GitHub Projects → Import to Jira
```

---

## 🎯 Best Practices

### 1. Use Environment Variables

```bash
# Never hardcode credentials
# Always use environment variables
export GITHUB_TOKEN=...
export GITLAB_TOKEN=...
export JIRA_API_TOKEN=...
```

### 2. Test in Staging First

```bash
# Use test repositories/projects
agentmesh:
  github:
    repo-owner: acme-corp
    repo-name: test-repo  # Not production!
```

### 3. Monitor Provider Health

```bash
# Check provider connectivity
curl http://localhost:8080/actuator/health

# View metrics
curl http://localhost:8080/actuator/prometheus | grep vcs_provider
```

### 4. Implement Circuit Breakers

```java
// Add Resilience4j for fault tolerance
@CircuitBreaker(name = "vcsProvider", fallbackMethod = "fallbackCreate")
public String createPullRequest(...) {
    // VCS API call
}

public String fallbackCreate(...) {
    // Fallback logic
    return "PR creation queued for retry";
}
```

---

## 🐛 Troubleshooting

### Issue: Provider Not Loading

```bash
# Check configuration
curl http://localhost:8080/actuator/env | grep agentmesh.vcs.provider

# Verify conditional bean creation
# Look for: "gitlabVcsProvider" in logs
```

### Issue: Authentication Failures

```bash
# Test credentials manually
curl -H "Authorization: token $GITHUB_TOKEN" \
  https://api.github.com/user

curl -H "PRIVATE-TOKEN: $GITLAB_TOKEN" \
  https://gitlab.com/api/v4/user
```

### Issue: Webhook Not Triggering

```bash
# Check webhook deliveries in provider UI
# GitHub: Settings → Webhooks → Recent Deliveries
# GitLab: Settings → Webhooks → Recent events

# Test webhook manually
curl -X POST http://localhost:8080/api/github/webhook \
  -H "Content-Type: application/json" \
  -d @test-webhook.json
```

---

## 🎉 Summary

AgentMesh now supports **multiple VCS and PM providers** through:

✅ **Abstraction Layer** - Interface-based design  
✅ **Plugin Architecture** - Easy to add new providers  
✅ **Configuration-Based** - Switch providers via config  
✅ **Implemented Adapters:**
- GitHub (VCS + PM)
- GitLab (VCS)
- Jira (PM)

✅ **Ready to Implement:**
- Bitbucket, Azure DevOps (VCS)
- Azure Boards, Linear, Asana (PM)

✅ **Hybrid Support** - Mix and match providers  
✅ **Production Ready** - Full error handling  
✅ **Well Documented** - Complete setup guides  

**You can now use AgentMesh with your existing tools!**

---

**For implementation details, see:**
- VcsProvider interface
- ProjectManagementProvider interface
- Provider-specific adapters in `/adapters/` folders

**Last Updated:** October 31, 2025  
**Status:** ✅ PRODUCTION READY

