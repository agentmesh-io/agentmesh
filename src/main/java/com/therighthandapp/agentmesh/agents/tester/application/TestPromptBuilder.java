package com.therighthandapp.agentmesh.agents.tester.application;

import org.springframework.stereotype.Component;

/**
 * Application Service: Test Prompt Builder
 * 
 * Builds comprehensive prompts for the LLM to generate test suites.
 * Incorporates code artifact details, similar past test suites, and testing guidelines.
 */
@Component
public class TestPromptBuilder {
    
    /**
     * Builds a test generation prompt from code artifact JSON
     * 
     * @param codeArtifactJson The code artifact in JSON format
     * @param similarTests Context from similar past test suites
     * @return The complete prompt for LLM
     */
    public String buildTestPrompt(String codeArtifactJson, String similarTests) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an expert test automation engineer with deep knowledge of unit testing, ");
        prompt.append("integration testing, test-driven development, and software quality assurance.\n\n");
        
        prompt.append("# Task\n");
        prompt.append("Generate a comprehensive test suite for the following code artifact. ");
        prompt.append("Create unit tests, integration tests, and E2E tests that ensure code quality, ");
        prompt.append("correctness, and maintainability.\n\n");
        
        prompt.append("# Testing Focus Areas\n");
        prompt.append("1. **Unit Tests**: Test individual methods and classes in isolation\n");
        prompt.append("   - Positive cases (happy path)\n");
        prompt.append("   - Negative cases (error handling)\n");
        prompt.append("   - Boundary cases (edge conditions)\n");
        prompt.append("   - Exception handling\n\n");
        
        prompt.append("2. **Integration Tests**: Test component interactions\n");
        prompt.append("   - Service layer integration\n");
        prompt.append("   - Database interactions\n");
        prompt.append("   - External API calls\n");
        prompt.append("   - Message queue integration\n\n");
        
        prompt.append("3. **E2E Tests**: Test complete user workflows\n");
        prompt.append("   - End-to-end scenarios\n");
        prompt.append("   - User journey validation\n");
        prompt.append("   - System integration\n\n");
        
        prompt.append("4. **Coverage Analysis**: Identify test gaps\n");
        prompt.append("   - Uncovered lines and branches\n");
        prompt.append("   - Missing test cases\n");
        prompt.append("   - Edge cases not tested\n\n");
        
        prompt.append("# Code Artifact to Test\n");
        prompt.append("```json\n");
        prompt.append(codeArtifactJson);
        prompt.append("\n```\n\n");
        
        if (similarTests != null && !similarTests.isBlank()) {
            prompt.append("# Similar Past Test Suites (for context)\n");
            prompt.append(similarTests);
            prompt.append("\n\n");
        }
        
        prompt.append("# Output Format\n");
        prompt.append("Generate the test suite in the following JSON format:\n\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"unitTests\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"className\": \"UserService\",\n");
        prompt.append("      \"methodName\": \"createUser\",\n");
        prompt.append("      \"testName\": \"testCreateUser_ValidInput_Success\",\n");
        prompt.append("      \"description\": \"Test creating user with valid input\",\n");
        prompt.append("      \"type\": \"POSITIVE\",\n");
        prompt.append("      \"testCases\": [\"valid user data\", \"all required fields present\"],\n");
        prompt.append("      \"testCode\": \"@Test\\npublic void testCreateUser_ValidInput_Success() {\\n  // Arrange\\n  User user = new User(\\\"john\\\", \\\"john@example.com\\\");\\n  // Act\\n  User result = userService.createUser(user);\\n  // Assert\\n  assertNotNull(result.getId());\\n  assertEquals(\\\"john\\\", result.getName());\\n}\",\n");
        prompt.append("      \"assertions\": [\"assertNotNull(result.getId())\", \"assertEquals(\\\"john\\\", result.getName())\"],\n");
        prompt.append("      \"mockedDependencies\": [\"UserRepository\"],\n");
        prompt.append("      \"filePath\": \"src/test/java/UserServiceTest.java\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"integrationTests\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"testName\": \"testUserCreationWorkflow\",\n");
        prompt.append("      \"description\": \"Test complete user creation workflow\",\n");
        prompt.append("      \"componentsUnderTest\": [\"UserService\", \"UserRepository\", \"Database\"],\n");
        prompt.append("      \"testCode\": \"@Test\\npublic void testUserCreationWorkflow() { ... }\",\n");
        prompt.append("      \"dependencies\": [\"Database\", \"UserRepository\"],\n");
        prompt.append("      \"setupCode\": \"@BeforeEach\\npublic void setup() { database.connect(); }\",\n");
        prompt.append("      \"teardownCode\": \"@AfterEach\\npublic void teardown() { database.cleanup(); }\",\n");
        prompt.append("      \"filePath\": \"src/test/java/integration/UserIntegrationTest.java\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"e2eTests\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"testName\": \"testCompleteUserRegistration\",\n");
        prompt.append("      \"description\": \"Test end-to-end user registration\",\n");
        prompt.append("      \"scenario\": \"New user registers and receives confirmation\",\n");
        prompt.append("      \"steps\": [\"Navigate to registration\", \"Fill form\", \"Submit\", \"Verify email\"],\n");
        prompt.append("      \"testCode\": \"@Test\\npublic void testCompleteUserRegistration() { ... }\",\n");
        prompt.append("      \"expectedOutcome\": \"User registered and email sent\",\n");
        prompt.append("      \"filePath\": \"src/test/java/e2e/UserRegistrationE2ETest.java\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"coverage\": {\n");
        prompt.append("    \"overallCoverage\": 85.5,\n");
        prompt.append("    \"lineCoverage\": 88.0,\n");
        prompt.append("    \"branchCoverage\": 82.0,\n");
        prompt.append("    \"methodCoverage\": 90.0,\n");
        prompt.append("    \"totalLines\": 1000,\n");
        prompt.append("    \"coveredLines\": 880,\n");
        prompt.append("    \"totalBranches\": 150,\n");
        prompt.append("    \"coveredBranches\": 123,\n");
        prompt.append("    \"totalMethods\": 50,\n");
        prompt.append("    \"coveredMethods\": 45,\n");
        prompt.append("    \"fileCoverage\": {\n");
        prompt.append("      \"UserService.java\": {\n");
        prompt.append("        \"filePath\": \"src/main/java/UserService.java\",\n");
        prompt.append("        \"coverage\": 90.0,\n");
        prompt.append("        \"totalLines\": 100,\n");
        prompt.append("        \"coveredLines\": 90,\n");
        prompt.append("        \"uncoveredLines\": [45, 67, 89]\n");
        prompt.append("      }\n");
        prompt.append("    }\n");
        prompt.append("  },\n");
        prompt.append("  \"qualityMetrics\": {\n");
        prompt.append("    \"totalTests\": 25,\n");
        prompt.append("    \"unitTestCount\": 20,\n");
        prompt.append("    \"integrationTestCount\": 4,\n");
        prompt.append("    \"e2eTestCount\": 1,\n");
        prompt.append("    \"assertionDensity\": 3.2,\n");
        prompt.append("    \"mockUsageRatio\": 0.6,\n");
        prompt.append("    \"testComplexity\": 4.5,\n");
        prompt.append("    \"qualityGrade\": \"A\"\n");
        prompt.append("  },\n");
        prompt.append("  \"testGaps\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"type\": \"MISSING_EDGE_CASE\",\n");
        prompt.append("      \"description\": \"No test for null input handling\",\n");
        prompt.append("      \"component\": \"UserService\",\n");
        prompt.append("      \"filePath\": \"src/main/java/UserService.java\",\n");
        prompt.append("      \"methodName\": \"createUser\",\n");
        prompt.append("      \"severity\": \"HIGH\",\n");
        prompt.append("      \"recommendation\": \"Add test for null user input\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"recommendations\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"category\": \"Coverage\",\n");
        prompt.append("      \"title\": \"Increase Branch Coverage\",\n");
        prompt.append("      \"description\": \"Add tests for uncovered branches in error handling\",\n");
        prompt.append("      \"rationale\": \"Error paths are critical for robustness\",\n");
        prompt.append("      \"impactScore\": 8,\n");
        prompt.append("      \"suggestedTestCode\": \"@Test\\npublic void testErrorHandling() { ... }\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n");
        prompt.append("```\n\n");
        
        prompt.append("# Instructions\n");
        prompt.append("1. Analyze ALL source files in the code artifact\n");
        prompt.append("2. Generate comprehensive unit tests for each public method\n");
        prompt.append("3. Create integration tests for component interactions\n");
        prompt.append("4. Design E2E tests for main user workflows\n");
        prompt.append("5. Calculate realistic coverage metrics based on generated tests\n");
        prompt.append("6. Identify test gaps and missing test cases\n");
        prompt.append("7. Provide actionable recommendations for test improvements\n");
        prompt.append("8. Use appropriate test frameworks (JUnit 5, Mockito, etc.)\n");
        prompt.append("9. Follow AAA pattern (Arrange, Act, Assert) in test code\n");
        prompt.append("10. Include meaningful test names that describe what is being tested\n");
        prompt.append("11. Return ONLY valid JSON with no additional commentary\n\n");
        
        prompt.append("Generate the comprehensive test suite now:");
        
        return prompt.toString();
    }
}
