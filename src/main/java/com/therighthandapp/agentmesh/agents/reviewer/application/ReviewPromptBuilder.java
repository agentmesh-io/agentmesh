package com.therighthandapp.agentmesh.agents.reviewer.application;

import org.springframework.stereotype.Component;

/**
 * Application Service: Review Prompt Builder
 * 
 * Builds comprehensive prompts for the LLM to generate code reviews.
 * Incorporates code artifact details, similar past reviews, and review guidelines.
 */
@Component
public class ReviewPromptBuilder {
    
    /**
     * Builds a review prompt from code artifact JSON
     * 
     * @param codeArtifactJson The code artifact in JSON format
     * @param similarReviews Context from similar past reviews
     * @return The complete prompt for LLM
     */
    public String buildReviewPrompt(String codeArtifactJson, String similarReviews) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an expert code reviewer with deep knowledge of software engineering best practices, ");
        prompt.append("security vulnerabilities, design patterns, and code quality standards.\n\n");
        
        prompt.append("# Task\n");
        prompt.append("Perform a comprehensive code review of the following code artifact. ");
        prompt.append("Analyze the code for quality issues, security vulnerabilities, best practice violations, ");
        prompt.append("and provide actionable improvement suggestions.\n\n");
        
        prompt.append("# Review Focus Areas\n");
        prompt.append("1. **Code Quality**: Readability, maintainability, complexity, code smells\n");
        prompt.append("2. **Security**: SQL injection, XSS, authentication issues, hardcoded secrets, CWE violations\n");
        prompt.append("3. **Best Practices**: Naming conventions, error handling, logging, documentation\n");
        prompt.append("4. **Performance**: Inefficient algorithms, resource leaks, database query optimization\n");
        prompt.append("5. **Architecture**: SOLID principles, layer separation, dependency management\n");
        prompt.append("6. **Testing**: Test coverage gaps, missing edge cases\n\n");
        
        prompt.append("# Code Artifact to Review\n");
        prompt.append("```json\n");
        prompt.append(codeArtifactJson);
        prompt.append("\n```\n\n");
        
        if (similarReviews != null && !similarReviews.isBlank()) {
            prompt.append("# Similar Past Reviews (for context)\n");
            prompt.append(similarReviews);
            prompt.append("\n\n");
        }
        
        prompt.append("# Output Format\n");
        prompt.append("Generate the review report in the following JSON format:\n\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"overallScore\": {\n");
        prompt.append("    \"totalScore\": 85,\n");
        prompt.append("    \"qualityScore\": 80,\n");
        prompt.append("    \"securityScore\": 90,\n");
        prompt.append("    \"maintainabilityScore\": 85,\n");
        prompt.append("    \"grade\": \"B+\",\n");
        prompt.append("    \"summary\": \"Overall assessment summary\"\n");
        prompt.append("  },\n");
        prompt.append("  \"qualityIssues\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"severity\": \"HIGH\",\n");
        prompt.append("      \"category\": \"Code Smell\",\n");
        prompt.append("      \"title\": \"Long Method Detected\",\n");
        prompt.append("      \"description\": \"Detailed description\",\n");
        prompt.append("      \"filePath\": \"src/main/java/Service.java\",\n");
        prompt.append("      \"lineNumber\": 45,\n");
        prompt.append("      \"codeSnippet\": \"public void processOrder() { ... }\",\n");
        prompt.append("      \"recommendation\": \"Extract methods to reduce complexity\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"securityIssues\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"severity\": \"CRITICAL\",\n");
        prompt.append("      \"category\": \"SQL Injection\",\n");
        prompt.append("      \"title\": \"Unsanitized SQL Query\",\n");
        prompt.append("      \"description\": \"Detailed description\",\n");
        prompt.append("      \"filePath\": \"src/main/java/Repository.java\",\n");
        prompt.append("      \"lineNumber\": 78,\n");
        prompt.append("      \"codeSnippet\": \"query = 'SELECT * FROM users WHERE id=' + userId\",\n");
        prompt.append("      \"cweId\": \"CWE-89\",\n");
        prompt.append("      \"remediation\": \"Use parameterized queries\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"bestPracticeViolations\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"category\": \"Exception Handling\",\n");
        prompt.append("      \"title\": \"Empty Catch Block\",\n");
        prompt.append("      \"description\": \"Exception caught but not handled\",\n");
        prompt.append("      \"filePath\": \"src/main/java/Service.java\",\n");
        prompt.append("      \"lineNumber\": 92,\n");
        prompt.append("      \"codeSnippet\": \"catch (Exception e) {}\",\n");
        prompt.append("      \"recommendation\": \"Log exception or rethrow as custom exception\",\n");
        prompt.append("      \"reference\": \"https://docs.oracle.com/javase/tutorial/essential/exceptions/\"\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"suggestions\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"category\": \"Refactoring\",\n");
        prompt.append("      \"title\": \"Extract Method\",\n");
        prompt.append("      \"description\": \"Complex method can be simplified\",\n");
        prompt.append("      \"filePath\": \"src/main/java/Service.java\",\n");
        prompt.append("      \"lineNumber\": 120,\n");
        prompt.append("      \"currentCode\": \"public void process() { /* 50 lines */ }\",\n");
        prompt.append("      \"suggestedCode\": \"public void process() { validateInput(); processData(); saveResult(); }\",\n");
        prompt.append("      \"benefit\": \"Improves readability and testability\",\n");
        prompt.append("      \"impactScore\": 8\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"codeMetrics\": {\n");
        prompt.append("    \"totalFiles\": 12,\n");
        prompt.append("    \"totalLines\": 1500,\n");
        prompt.append("    \"codeLines\": 1200,\n");
        prompt.append("    \"commentLines\": 300,\n");
        prompt.append("    \"commentRatio\": 0.25,\n");
        prompt.append("    \"duplicatedLines\": 50,\n");
        prompt.append("    \"duplicationRatio\": 0.04,\n");
        prompt.append("    \"languageDistribution\": {\"java\": 100}\n");
        prompt.append("  },\n");
        prompt.append("  \"complexityAnalysis\": {\n");
        prompt.append("    \"averageCyclomaticComplexity\": 4.5,\n");
        prompt.append("    \"maxCyclomaticComplexity\": 15,\n");
        prompt.append("    \"mostComplexMethod\": \"processOrder()\",\n");
        prompt.append("    \"mostComplexFile\": \"OrderService.java\",\n");
        prompt.append("    \"complexMethods\": [\"processOrder()\", \"validatePayment()\"],\n");
        prompt.append("    \"cognitiveComplexity\": 78,\n");
        prompt.append("    \"complexityGrade\": \"B\"\n");
        prompt.append("  }\n");
        prompt.append("}\n");
        prompt.append("```\n\n");
        
        prompt.append("# Instructions\n");
        prompt.append("1. Analyze ALL source files in the code artifact\n");
        prompt.append("2. Identify quality issues with appropriate severity levels (CRITICAL, HIGH, MEDIUM, LOW, INFO)\n");
        prompt.append("3. Look for security vulnerabilities and reference CWE IDs when applicable\n");
        prompt.append("4. Check for best practice violations with actionable recommendations\n");
        prompt.append("5. Provide concrete code improvement suggestions with before/after examples\n");
        prompt.append("6. Calculate accurate code metrics from the source files\n");
        prompt.append("7. Analyze complexity and identify methods/files that need refactoring\n");
        prompt.append("8. Assign overall scores (0-100) and a letter grade (A+, A, B, C, D, F)\n");
        prompt.append("9. Ensure all issues include file path, line number, and code snippet\n");
        prompt.append("10. Return ONLY valid JSON with no additional commentary\n\n");
        
        prompt.append("Generate the comprehensive review report now:");
        
        return prompt.toString();
    }
}
