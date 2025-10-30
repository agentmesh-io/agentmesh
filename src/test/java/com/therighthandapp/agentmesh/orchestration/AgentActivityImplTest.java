package com.therighthandapp.agentmesh.orchestration;

import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import com.therighthandapp.agentmesh.llm.MockLLMClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class AgentActivityImplTest {

    @Autowired
    private AgentActivityImpl agentActivity;

    @Autowired
    private BlackboardService blackboard;

    @Autowired
    private MockLLMClient mockLLM;

    @BeforeEach
    public void setUp() {
        mockLLM.reset();
    }

    @Test
    public void testExecutePlanning() {
        // Configure mock responses
        String srsContent = "Build a REST API for user management";
        String expectedPlan = "1. Design database schema\n2. Implement CRUD endpoints\n3. Add authentication";

        mockLLM.setDefaultResponse(expectedPlan);

        // Execute
        String planId = agentActivity.executePlanning(srsContent);

        // Verify
        assertThat(planId).isNotNull();
        assertThat(mockLLM.getCallHistory()).hasSize(1);
        assertThat(mockLLM.getCallHistory().get(0).getMethod()).isEqualTo("complete");

        // Verify plan was posted to Blackboard
        var entry = blackboard.getById(Long.parseLong(planId));
        assertThat(entry).isPresent();
        assertThat(entry.get().getEntryType()).isEqualTo("PLAN");
        assertThat(entry.get().getContent()).contains(expectedPlan);
    }

    @Test
    public void testExecuteCodeGeneration() {
        // First, create a plan entry
        var planEntry = blackboard.post("test", "PLAN", "Test Plan", "Task breakdown for user API");
        String planId = planEntry.getId().toString();

        // Configure mock response
        String expectedCode = "public class UserController { ... }";
        mockLLM.setDefaultResponse(expectedCode);

        // Execute
        String codeId = agentActivity.executeCodeGeneration(planId, "Implement UserController");

        // Verify
        assertThat(codeId).isNotNull();
        assertThat(mockLLM.getCallHistory()).hasSize(1);
        assertThat(mockLLM.getCallHistory().get(0).getMethod()).isEqualTo("chat");

        var entry = blackboard.getById(Long.parseLong(codeId));
        assertThat(entry).isPresent();
        assertThat(entry.get().getEntryType()).isEqualTo("CODE");
        assertThat(entry.get().getContent()).contains(expectedCode);
    }

    @Test
    public void testExecuteCodeReview() {
        // First, create a code entry
        var codeEntry = blackboard.post("test", "CODE", "UserController", "public class UserController {}");
        String codeId = codeEntry.getId().toString();

        // Configure mock response
        String expectedReview = "Code looks good. Consider adding input validation.";
        mockLLM.setDefaultResponse(expectedReview);

        // Execute
        String reviewId = agentActivity.executeCodeReview(codeId);

        // Verify
        assertThat(reviewId).isNotNull();
        var entry = blackboard.getById(Long.parseLong(reviewId));
        assertThat(entry).isPresent();
        assertThat(entry.get().getEntryType()).isEqualTo("REVIEW");
        assertThat(entry.get().getContent()).contains(expectedReview);
    }

    @Test
    public void testExecuteTestGeneration() {
        // First, create a code entry
        var codeEntry = blackboard.post("test", "CODE", "Calculator", "public class Calculator { public int add(int a, int b) { return a + b; } }");
        String codeId = codeEntry.getId().toString();

        // Configure mock response
        String expectedTests = "@Test\npublic void testAdd() { ... }";
        mockLLM.setDefaultResponse(expectedTests);

        // Execute
        String testId = agentActivity.executeTestGeneration(codeId);

        // Verify
        assertThat(testId).isNotNull();
        var entry = blackboard.getById(Long.parseLong(testId));
        assertThat(entry).isPresent();
        assertThat(entry.get().getEntryType()).isEqualTo("TEST");
        assertThat(entry.get().getContent()).contains(expectedTests);
    }

    @Test
    public void testExecuteDebug() {
        // First, create a test failure entry
        var failureEntry = blackboard.post("test", "TEST_FAILURE", "Null Pointer", "NPE in UserController.getUser()");
        String failureId = failureEntry.getId().toString();

        // Configure mock response
        String expectedDebug = "Root cause: Missing null check. Fix: Add if (user == null) check.";
        mockLLM.setDefaultResponse(expectedDebug);

        // Execute
        String debugId = agentActivity.executeDebug(failureId);

        // Verify
        assertThat(debugId).isNotNull();
        var entry = blackboard.getById(Long.parseLong(debugId));
        assertThat(entry).isPresent();
        assertThat(entry.get().getEntryType()).isEqualTo("DEBUG");
        assertThat(entry.get().getContent()).contains(expectedDebug);
    }

    @Test
    public void testTokenUsageTracking() {
        mockLLM.setDefaultResponse("Plan with many details...");

        agentActivity.executePlanning("Simple SRS");

        // Verify token usage was tracked
        assertThat(mockLLM.getLastUsage()).isNotNull();
        assertThat(mockLLM.getLastUsage().getTotalTokens()).isGreaterThan(0);
        assertThat(mockLLM.getLastUsage().getEstimatedCost()).isGreaterThan(0);
    }

    @Test
    public void testFullWorkflowSequence() {
        // Configure sequential responses
        mockLLM.setDefaultResponse("Step response");

        // 1. Planning
        String planId = agentActivity.executePlanning("Build a calculator");

        // 2. Code generation
        String codeId = agentActivity.executeCodeGeneration(planId, "Implement add function");

        // 3. Test generation
        String testId = agentActivity.executeTestGeneration(codeId);

        // 4. Code review
        String reviewId = agentActivity.executeCodeReview(codeId);

        // Verify all steps completed
        assertThat(planId).isNotNull();
        assertThat(codeId).isNotNull();
        assertThat(testId).isNotNull();
        assertThat(reviewId).isNotNull();

        // Verify 4 LLM calls were made
        assertThat(mockLLM.getCallHistory()).hasSize(4);
    }
}

