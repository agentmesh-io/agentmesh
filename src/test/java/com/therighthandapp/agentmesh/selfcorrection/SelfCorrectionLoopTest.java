package com.therighthandapp.agentmesh.selfcorrection;

import com.therighthandapp.agentmesh.blackboard.BlackboardService;
import com.therighthandapp.agentmesh.llm.MockLLMClient;
import com.therighthandapp.agentmesh.mast.MASTValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class SelfCorrectionLoopTest {

    @Autowired
    private SelfCorrectionLoop selfCorrectionLoop;

    @Autowired
    private MockLLMClient mockLLM;

    @Autowired
    private BlackboardService blackboard;

    @Autowired
    private MASTValidator mastValidator;

    @BeforeEach
    public void setUp() {
        mockLLM.reset();
    }

    @Test
    public void testSuccessfulCorrectionFirstAttempt() {
        String validCode = "public class Calculator {\n  public int add(int a, int b) {\n    return a + b;\n  }\n}";
        mockLLM.setDefaultResponse(validCode);

        List<String> requirements = Arrays.asList("class", "add");

        CorrectionResult result = selfCorrectionLoop.correctUntilValid(
                "test-agent", "Create Calculator class", requirements
        );

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getIterationCount()).isEqualTo(1);
        assertThat(result.getOutput()).contains("Calculator");
    }

    @Test
    public void testCorrectionAfterMultipleAttempts() {
        // Set default response to incomplete code (will fail validation)
        mockLLM.setDefaultResponse("public class Calculator {");

        List<String> requirements = Arrays.asList("class", "add");

        CorrectionResult result = selfCorrectionLoop.correctUntilValid(
                "test-agent", "Create Calculator class", requirements
        );

        // Should iterate multiple times and fail (code never contains "add")
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getIterationCount()).isGreaterThan(1);
        assertThat(mockLLM.getCallHistory().size()).isGreaterThan(2); // Generate + reflect calls
    }

    @Test
    public void testMaxIterationsReached() {
        // Always return invalid code
        mockLLM.setDefaultResponse("invalid code");

        List<String> requirements = Arrays.asList("class", "add");

        CorrectionResult result = selfCorrectionLoop.correctUntilValid(
                "test-agent", "Create Calculator class", requirements
        );

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureReason()).contains("Max iterations");
    }

    @Test
    public void testEmptyOutputHandling() {
        mockLLM.setDefaultResponse("");

        List<String> requirements = Arrays.asList("class");

        CorrectionResult result = selfCorrectionLoop.correctUntilValid(
                "test-agent", "Create class", requirements
        );

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    public void testRequirementValidation() {
        String code = "public class Calculator { }";
        mockLLM.setDefaultResponse(code);

        // Missing "add" method
        List<String> requirements = Arrays.asList("class", "add");

        CorrectionResult result = selfCorrectionLoop.correctUntilValid(
                "test-agent", "Create Calculator", requirements
        );

        // Should fail because code doesn't contain "add"
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    public void testLLMCallsInLoop() {
        String validCode = "public class Test {\n  public void method() {}\n}";
        mockLLM.setDefaultResponse(validCode);

        List<String> requirements = Arrays.asList("class", "method");

        selfCorrectionLoop.correctUntilValid("test-agent", "Create class", requirements);

        // Should have multiple LLM calls (generate + reflect)
        assertThat(mockLLM.getCallHistory()).isNotEmpty();

        // At least one "complete" or "chat" call
        boolean hasGenerateCall = mockLLM.getCallHistory().stream()
                .anyMatch(call -> call.getMethod().equals("chat") || call.getMethod().equals("complete"));
        assertThat(hasGenerateCall).isTrue();
    }

    @Test
    public void testBlackboardIntegration() {
        String validCode = "public class User { private String name; }";
        mockLLM.setDefaultResponse(validCode);

        List<String> requirements = Arrays.asList("class", "User");

        CorrectionResult result = selfCorrectionLoop.correctUntilValid(
                "test-agent", "Create User class", requirements
        );

        assertThat(result.isSuccess()).isTrue();

        // Verify final code was posted to Blackboard
        var entries = blackboard.readByType("CODE_FINAL");
        assertThat(entries).isNotEmpty();
    }
}

