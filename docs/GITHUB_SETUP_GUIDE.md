# GitHub Integration Setup Guide

## 🚀 Quick Start (30 minutes)

This guide will walk you through setting up the complete GitHub integration for AgentMesh.

---

## Prerequisites

- ✅ AgentMesh backend running
- ✅ GitHub repository (public or private)
- ✅ GitHub Personal Access Token with appropriate permissions
- ✅ GitHub CLI installed (optional but recommended)

---

## Step 1: Create GitHub Personal Access Token (5 min)

1. Go to https://github.com/settings/tokens
2. Click "Generate new token (classic)"
3. Select scopes:
   - ✅ `repo` (full control of private repositories)
   - ✅ `write:discussion` (for issue comments)
   - ✅ `project` (for GitHub Projects integration)
4. Click "Generate token"
5. **Copy the token** (you won't see it again!)

```bash
# Save token as environment variable
export GITHUB_TOKEN=ghp_your_token_here
```

---

## Step 2: Configure AgentMesh (5 min)

### Update application.yml

```yaml
agentmesh:
  github:
    enabled: true
    token: ${GITHUB_TOKEN}
    repo-owner: your-github-username  # or organization name
    repo-name: your-repository-name
    webhook-secret: ${GITHUB_WEBHOOK_SECRET:mysecret123}
    
    projects:
      enabled: false  # We'll enable this later
      project-id: ${GITHUB_PROJECT_ID:}
```

### Or set via environment variables

```bash
export GITHUB_TOKEN=ghp_your_token_here
export GITHUB_REPO_OWNER=your-username
export GITHUB_REPO_NAME=your-repo
export GITHUB_WEBHOOK_SECRET=mysecret123
```

### Restart AgentMesh

```bash
mvn spring-boot:run
```

---

## Step 3: Configure GitHub Webhook (5 min)

### Option A: Using GitHub CLI (Recommended)

```bash
# Install gh if not already installed
# macOS: brew install gh
# Linux: See https://github.com/cli/cli/releases

# Login to GitHub
gh auth login

# Create webhook
gh api repos/YOUR_USERNAME/YOUR_REPO/hooks \
  --method POST \
  --field name=web \
  --field active=true \
  --field events[]=issues \
  --field events[]=pull_request \
  --field events[]=issue_comment \
  --field config[url]=https://your-server.com/api/github/webhook \
  --field config[content_type]=json \
  --field config[secret]=mysecret123
```

### Option B: Using GitHub Web UI

1. Go to your repository on GitHub
2. Click **Settings** → **Webhooks** → **Add webhook**
3. Fill in:
   - **Payload URL**: `https://your-server.com/api/github/webhook`
   - **Content type**: `application/json`
   - **Secret**: `mysecret123` (same as GITHUB_WEBHOOK_SECRET)
   - **Which events**: Select:
     - ✅ Issues
     - ✅ Pull requests
     - ✅ Issue comments
   - ✅ **Active**
4. Click **Add webhook**

### For Local Testing with ngrok

```bash
# Install ngrok: brew install ngrok

# Expose local port
ngrok http 8080

# Use the ngrok URL as webhook URL
# Example: https://abc123.ngrok.io/api/github/webhook
```

---

## Step 4: Create GitHub Actions Workflow (5 min)

Create `.github/workflows/agentmesh.yml`:

```yaml
name: AgentMesh Integration

on:
  issues:
    types: [opened, labeled]
  pull_request:
    types: [opened, closed]
  issue_comment:
    types: [created]

jobs:
  notify:
    runs-on: ubuntu-latest
    if: contains(github.event.issue.labels.*.name, 'agentmesh')
    
    steps:
      - name: Comment on Issue
        uses: actions/github-script@v6
        with:
          script: |
            if (context.eventName === 'issues' && context.payload.action === 'opened') {
              github.rest.issues.createComment({
                issue_number: context.issue.number,
                owner: context.repo.owner,
                repo: context.repo.repo,
                body: '🤖 AgentMesh is processing your request. Track progress here!'
              });
            }
```

Commit and push:

```bash
git add .github/workflows/agentmesh.yml
git commit -m "Add AgentMesh workflow"
git push
```

---

## Step 5: Test the Integration (10 min)

### Create Test Issue

```bash
# Using GitHub CLI
gh issue create \
  --title "Test: Add Calculator API" \
  --body "Create a simple Calculator class with add and subtract methods" \
  --label "agentmesh"

# Or create manually via GitHub UI with label "agentmesh"
```

### Monitor AgentMesh Logs

```bash
# Watch logs
tail -f logs/spring.log | grep -E "GitHub|Webhook|Issue"
```

### Expected Flow

1. **Issue Created** → Webhook received
2. **Blackboard Updated** → SRS entry created
3. **Comment Added** → "AgentMesh received your request..."
4. **Labels Updated** → "agentmesh-processing" added
5. **Code Generation** → Self-correction loop runs
6. **PR Created** → Pull request with generated code
7. **Final Comment** → "PR created: [link]"

### Check Results

```bash
# Check Blackboard
curl http://localhost:8080/api/blackboard/entries/type/SRS

# List PRs
gh pr list

# View PR
gh pr view [NUMBER]
```

---

## Step 6: Enable GitHub Projects (Optional - 10 min)

### Create Project

```bash
# Via GitHub UI
# 1. Go to repository → Projects → New project
# 2. Choose "Board" template
# 3. Name: "AgentMesh Development"
```

### Get Project ID

```bash
# Using GraphQL
curl -H "Authorization: bearer $GITHUB_TOKEN" \
  -X POST \
  -d '{"query":"query{viewer{projectsV2(first:10){nodes{id title}}}}"}' \
  https://api.github.com/graphql | jq
  
# Look for your project and copy the "id" field
# Example: PVT_kwDOABC123
```

### Update Configuration

```yaml
agentmesh:
  github:
    projects:
      enabled: true
      project-id: PVT_kwDOABC123  # Your project ID
```

### Restart AgentMesh

```bash
mvn spring-boot:run
```

---

## Testing Checklist

- [ ] AgentMesh starts without errors
- [ ] Webhook endpoint accessible: `curl http://localhost:8080/api/github/webhook`
- [ ] GitHub webhook shows successful delivery
- [ ] Issue with "agentmesh" label triggers workflow
- [ ] Comment added to issue
- [ ] Labels updated
- [ ] Code generated (check logs)
- [ ] PR created successfully
- [ ] Projects integration working (if enabled)

---

## Troubleshooting

### Webhook Not Triggering

```bash
# Check webhook deliveries in GitHub
# Settings → Webhooks → Your webhook → Recent Deliveries

# Check AgentMesh is receiving requests
tail -f logs/spring.log | grep Webhook

# Test webhook manually
curl -X POST http://localhost:8080/api/github/webhook \
  -H "X-GitHub-Event: issues" \
  -H "Content-Type: application/json" \
  -d '{"action":"opened","issue":{"number":1,"title":"Test","body":"Test","labels":[{"name":"agentmesh"}]}}'
```

### Authentication Errors

```bash
# Verify token has correct permissions
curl -H "Authorization: token $GITHUB_TOKEN" \
  https://api.github.com/user

# Check token scopes
curl -I -H "Authorization: token $GITHUB_TOKEN" \
  https://api.github.com/user | grep X-OAuth-Scopes
```

### PR Creation Fails

```bash
# Check logs for detailed error
tail -f logs/spring.log | grep -A 10 "Error creating pull request"

# Verify repository access
gh repo view YOUR_USERNAME/YOUR_REPO

# Test PR creation manually
gh pr create --title "Test" --body "Test PR"
```

### Projects Integration Not Working

```bash
# Verify project ID is correct
# The ID must be the GraphQL node ID (starts with PVT_)

# Test GraphQL access
curl -H "Authorization: bearer $GITHUB_TOKEN" \
  -X POST \
  -d '{"query":"query{node(id:\"YOUR_PROJECT_ID\"){id}}"}' \
  https://api.github.com/graphql
```

---

## Usage Examples

### Simple Feature Request

```bash
gh issue create \
  --title "Add User Authentication" \
  --body "Implement JWT-based authentication with login and register endpoints" \
  --label "agentmesh"
```

### Bug Fix

```bash
gh issue create \
  --title "Fix NPE in UserService" \
  --body "NullPointerException when user not found. Add proper null check." \
  --label "agentmesh,bug"
```

### Complex Feature

```bash
gh issue create \
  --title "Add Payment Processing" \
  --body "Integrate Stripe payment processing with webhooks" \
  --label "agentmesh,feature"
```

---

## Advanced Configuration

### Custom Labels

```yaml
agentmesh:
  github:
    labels:
      trigger: agentmesh  # Label to trigger processing
      processing: agentmesh-processing
      done: agentmesh-done
      failed: agentmesh-failed
```

### Webhook Security

```yaml
agentmesh:
  github:
    webhook-secret: ${GITHUB_WEBHOOK_SECRET}
    verify-signature: true  # Enable signature verification
```

### Rate Limiting

```yaml
agentmesh:
  github:
    rate-limit:
      max-requests: 5000  # GitHub API limit
      per-hour: true
```

---

## Production Deployment

### Environment Variables

```bash
# .env file
GITHUB_TOKEN=ghp_your_production_token
GITHUB_REPO_OWNER=your-org
GITHUB_REPO_NAME=production-repo
GITHUB_WEBHOOK_SECRET=strong_secret_here
GITHUB_PROJECT_ID=PVT_kwDOPROD123
```

### Docker Compose

```yaml
services:
  agentmesh:
    image: agentmesh:latest
    environment:
      - GITHUB_TOKEN=${GITHUB_TOKEN}
      - GITHUB_REPO_OWNER=${GITHUB_REPO_OWNER}
      - GITHUB_REPO_NAME=${GITHUB_REPO_NAME}
      - GITHUB_WEBHOOK_SECRET=${GITHUB_WEBHOOK_SECRET}
      - AGENTMESH_GITHUB_ENABLED=true
```

### Kubernetes Secret

```bash
kubectl create secret generic github-credentials \
  --from-literal=token=$GITHUB_TOKEN \
  --from-literal=webhook-secret=$GITHUB_WEBHOOK_SECRET
```

---

## Next Steps

1. ✅ **Test with real issues** - Create issues and watch AgentMesh work
2. ✅ **Review generated PRs** - Check code quality and make adjustments
3. ✅ **Configure Projects** - Set up project board for better tracking
4. ✅ **Add custom fields** - Track iterations, violations, etc.
5. ✅ **Train your team** - Show them how to use the integration

---

## Support

**Documentation:** 
- `INTEGRATION_OPTIONS.md` - Full integration guide
- `TEST_SCENARIOS.md` - Testing scenarios

**Common Issues:**
- Check logs: `tail -f logs/spring.log`
- Verify config: `curl http://localhost:8080/actuator/env`
- Test webhook: GitHub Settings → Webhooks → Redeliver

**Community:**
- GitHub Issues: Report problems
- GitHub Discussions: Ask questions

---

**Setup Complete! 🎉**

Your GitHub integration is now ready. Create an issue with the "agentmesh" label to see it in action!

