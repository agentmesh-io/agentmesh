package com.therighthandapp.agentmesh.github;

import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import com.therighthandapp.agentmesh.selfcorrection.CorrectionResult;
import com.therighthandapp.agentmesh.selfcorrection.SelfCorrectionLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * GitHub webhook controller for issue and PR automation
 */
@RestController
@RequestMapping("/api/github")
@ConditionalOnProperty(name = "agentmesh.github.enabled", havingValue = "true")
public class GitHubWebhookController {
    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookController.class);

    private final GitHubIntegrationService githubService;
    private final BlackboardService blackboard;

    @Autowired(required = false)
    private GitHubProjectsService projectsService;

    @Autowired(required = false)
    private SelfCorrectionLoop selfCorrectionLoop;

    public GitHubWebhookController(GitHubIntegrationService githubService,
                                   BlackboardService blackboard) {
        this.githubService = githubService;
        this.blackboard = blackboard;
    }

    /**
     * Handle GitHub webhook events
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestBody GitHubEvent payload) {

        log.info("Received GitHub {} event", event);

        try {
            switch (event) {
                case "issues" -> handleIssueEvent(payload);
                case "pull_request" -> handlePullRequestEvent(payload);
                case "issue_comment" -> handleCommentEvent(payload);
                default -> log.info("Unhandled event type: {}", event);
            }

            return ResponseEntity.ok("Event processed");

        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    /**
     * Handle issue events (opened, labeled, etc.)
     */
    private void handleIssueEvent(GitHubEvent event) {
        if ("opened".equals(event.getAction()) || "labeled".equals(event.getAction())) {
            GitHubEvent.GitHubIssue issue = event.getIssue();

            // Only process issues with 'agentmesh' label
            if (issue != null && issue.hasLabel("agentmesh")) {
                log.info("Processing issue #{}: {}", issue.getNumber(), issue.getTitle());

                // Add to project if enabled
                String projectItemId = null;
                if (projectsService != null && issue.getNodeId() != null) {
                    projectItemId = projectsService.addIssueToProject(issue.getNodeId());
                }

                // Post to Blackboard as SRS
                blackboard.post("github-issue-" + issue.getNumber(), "SRS",
                              issue.getTitle(), issue.getBody() != null ? issue.getBody() : "");

                // Add comment
                githubService.addComment(issue.getNumber().toString(),
                    "🤖 AgentMesh received your request and added it to the project board. " +
                    "Planning phase starting...");

                // Update labels
                githubService.updateIssueLabels(issue.getNumber().toString(),
                    Arrays.asList("agentmesh", "agentmesh-processing"));

                // Trigger async workflow
                triggerAgentWorkflow(issue, projectItemId);
            }
        }
    }

    /**
     * Handle pull request events
     */
    private void handlePullRequestEvent(GitHubEvent event) {
        GitHubEvent.GitHubPullRequest pr = event.getPullRequest();

        if ("closed".equals(event.getAction()) && pr != null && pr.isMerged()) {
            log.info("PR #{} merged: {}", pr.getNumber(), pr.getTitle());
            // Could trigger post-merge actions here
        }
    }

    /**
     * Handle issue comment events
     */
    private void handleCommentEvent(GitHubEvent event) {
        GitHubEvent.GitHubComment comment = event.getComment();
        GitHubEvent.GitHubIssue issue = event.getIssue();

        if ("created".equals(event.getAction()) && comment != null && issue != null) {
            log.info("New comment on issue #{}: {}", issue.getNumber(),
                    comment.getBody() != null ? comment.getBody().substring(0, Math.min(50, comment.getBody().length())) : "");

            // Could handle commands like "@agentmesh retry" here
            if (comment.getBody() != null && comment.getBody().contains("@agentmesh retry")) {
                githubService.addComment(issue.getNumber().toString(),
                    "🤖 Retrying task... This feature will be available soon.");
            }
        }
    }

    /**
     * Trigger async agent workflow
     */
    private void triggerAgentWorkflow(GitHubEvent.GitHubIssue issue, String projectItemId) {
        CompletableFuture.runAsync(() -> {
            String issueNumber = issue.getNumber().toString();

            try {
                log.info("Starting workflow for issue #{}", issueNumber);

                // Phase 1: Planning (mock for now)
                githubService.addComment(issueNumber,
                    "🤖 **Phase 1: Planning**\\n\\nAnalyzing requirements...");
                Thread.sleep(2000); // Simulate planning

                // Phase 2: Code Generation with Self-Correction
                githubService.addComment(issueNumber,
                    "🤖 **Phase 2: Code Generation**\\n\\nGenerating code with self-correction...");

                CorrectionResult result = null;
                if (selfCorrectionLoop != null) {
                    // Use self-correction for quality
                    result = selfCorrectionLoop.correctUntilValid(
                        "github-coder",
                        "Generate code for: " + issue.getTitle() + "\\n\\n" + issue.getBody(),
                        Arrays.asList("class", "public", "method")
                    );
                } else {
                    // Mock result if self-correction not available
                    result = mockCodeGeneration(issue);
                }

                // Update projects with metrics
                if (projectsService != null && projectItemId != null) {
                    projectsService.addIterationMetrics(issueNumber,
                        result.getIterationCount(), result.isSuccess(), result.getFailureReason());
                }

                if (result.isSuccess()) {
                    // Phase 3: Create PR
                    githubService.addComment(issueNumber,
                        ("✅ **Code Generated Successfully!**\\n\\n" +
                            "- Self-correction iterations: %d\\n" +
                            "- Creating pull request...\\n").formatted(
                            result.getIterationCount()));

                    String prUrl = githubService.createPullRequest(
                        "[AgentMesh] " + issue.getTitle(),
                        result.getOutput(),
                        issueNumber
                    );

                    githubService.addComment(issueNumber,
                        "🎉 **Pull Request Created!**\\n\\n" +
                        "PR: " + prUrl + "\\n\\n" +
                        "Please review the generated code and merge when ready.");

                    githubService.updateIssueLabels(issueNumber,
                        Arrays.asList("agentmesh", "agentmesh-done", "review-needed"));

                } else {
                    // Failed
                    githubService.addComment(issueNumber,
                        ("⚠️ **Code Generation Failed**\\n\\n" +
                            "- Iterations attempted: %d\\n" +
                            "- Reason: %s\\n\\n" +
                            "Please review the requirements and try again. " +
                            "You can comment `@agentmesh retry` to retry.").formatted(
                            result.getIterationCount(), result.getFailureReason()));

                    githubService.updateIssueLabels(issueNumber,
                        Arrays.asList("agentmesh", "agentmesh-failed", "needs-attention"));
                }

            } catch (Exception e) {
                log.error("Workflow failed for issue #{}", issueNumber, e);
                githubService.addComment(issueNumber,
                    "❌ **Workflow Error**\\n\\n" +
                    "An error occurred: " + e.getMessage() + "\\n\\n" +
                    "Please contact support or try again later.");

                githubService.updateIssueLabels(issueNumber,
                    Arrays.asList("agentmesh", "agentmesh-error"));
            }
        });
    }

    /**
     * Mock code generation for testing without self-correction
     */
    private CorrectionResult mockCodeGeneration(GitHubEvent.GitHubIssue issue) {
        String code = """
            package com.generated;
            
            /**
             * Generated class for: %s
             * Auto-generated by AgentMesh
             */
            public class Feature%d {
                
                // TODO: Implement functionality
                public void execute() {
                    System.out.println("Feature implementation");
                }
            }
            """.formatted(issue.getTitle(), issue.getNumber());

        return CorrectionResult.success(code, 1);
    }
}

