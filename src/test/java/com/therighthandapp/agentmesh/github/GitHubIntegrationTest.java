package com.therighthandapp.agentmesh.github;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for GitHub integration components
 * Note: These are unit tests that don't require actual GitHub API access
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "agentmesh.github.enabled=false"  // Disabled for tests
})
public class GitHubIntegrationTest {

    @Test
    public void testGitHubEventParsing() {
        // Test GitHubEvent model
        GitHubEvent event = new GitHubEvent();
        event.setAction("opened");

        GitHubEvent.GitHubIssue issue = new GitHubEvent.GitHubIssue();
        issue.setNumber(123L);
        issue.setTitle("Test Issue");
        issue.setBody("Test body");
        issue.setNodeId("I_kwDOABC123");

        event.setIssue(issue);

        assertThat(event.getAction()).isEqualTo("opened");
        assertThat(event.getIssue()).isNotNull();
        assertThat(event.getIssue().getNumber()).isEqualTo(123L);
        assertThat(event.getIssue().getTitle()).isEqualTo("Test Issue");
    }

    @Test
    public void testGitHubLabelDetection() {
        GitHubEvent.GitHubIssue issue = new GitHubEvent.GitHubIssue();

        GitHubEvent.GitHubLabel label1 = new GitHubEvent.GitHubLabel();
        label1.setName("agentmesh");

        GitHubEvent.GitHubLabel label2 = new GitHubEvent.GitHubLabel();
        label2.setName("feature");

        issue.setLabels(java.util.Arrays.asList(label1, label2));

        assertThat(issue.hasLabel("agentmesh")).isTrue();
        assertThat(issue.hasLabel("feature")).isTrue();
        assertThat(issue.hasLabel("bug")).isFalse();
    }

    @Test
    public void testGitHubPullRequestModel() {
        GitHubEvent.GitHubPullRequest pr = new GitHubEvent.GitHubPullRequest();
        pr.setNumber(456L);
        pr.setTitle("Test PR");
        pr.setState("open");
        pr.setMerged(false);
        pr.setHtmlUrl("https://github.com/org/repo/pull/456");

        assertThat(pr.getNumber()).isEqualTo(456L);
        assertThat(pr.getTitle()).isEqualTo("Test PR");
        assertThat(pr.isMerged()).isFalse();
        assertThat(pr.getHtmlUrl()).contains("/pull/456");
    }

    @Test
    public void testGitHubRepositoryModel() {
        GitHubEvent.GitHubRepository repo = new GitHubEvent.GitHubRepository();
        repo.setName("test-repo");
        repo.setFullName("org/test-repo");

        GitHubEvent.GitHubUser owner = new GitHubEvent.GitHubUser();
        owner.setLogin("org");
        repo.setOwner(owner);

        assertThat(repo.getName()).isEqualTo("test-repo");
        assertThat(repo.getFullName()).isEqualTo("org/test-repo");
        assertThat(repo.getOwner().getLogin()).isEqualTo("org");
    }
}

