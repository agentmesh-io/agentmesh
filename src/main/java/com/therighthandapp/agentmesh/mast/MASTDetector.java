package com.therighthandapp.agentmesh.mast;

import com.therighthandapp.agentmesh.blackboard.BlackboardEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * MAST (Multi-Agent System Taxonomy) Failure Detector
 * Implements 14 failure mode detection algorithms to identify
 * agent collaboration issues in real-time.
 */
@Service
@Slf4j
public class MASTDetector {

    private final MASTViolationRepository violationRepository;
    private final Map<String, Instant> lastMemoryQuery = new HashMap<>();
    private final Map<String, BlackboardEntry> recentEntries = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BlackboardEntry> eldest) {
            return size() > 100; // Keep last 100 entries
        }
    };

    public MASTDetector(MASTViolationRepository violationRepository) {
        this.violationRepository = violationRepository;
    }

    /**
     * Track when an agent queries memory (for context loss detection)
     */
    public void trackMemoryQuery(String agentId) {
        lastMemoryQuery.put(agentId, Instant.now());
        log.debug("Tracked memory query for agent: {}", agentId);
    }

    /**
     * Analyze blackboard entry for potential violations
     */
    @Transactional
    public void analyzeBlackboardEntry(BlackboardEntry entry) {
        log.debug("Analyzing blackboard entry {} from agent {}", entry.getId(), entry.getAgentId());
        
        // Store entry for later analysis
        recentEntries.put(entry.getId() + "", entry);

        // FM-1.3: Context Loss Detection
        detectContextLoss(entry);

        // FM-1.2: Ambiguous Language Detection
        detectAmbiguousLanguage(entry);

        // FM-3.1: Test Coverage Below Threshold
        if ("TEST_RESULT".equals(entry.getEntryType())) {
            detectLowTestCoverage(entry);
        }

        // FM-2.2: Duplicate Work Detection
        detectDuplicateWork(entry);

        // FM-3.2: Unresolved Code Review Issues
        if ("REVIEW".equals(entry.getEntryType())) {
            detectUnresolvedReview(entry);
        }
    }

    /**
     * FM-1.4: Context Loss Detection
     * Agent posts code/decision without querying memory first
     */
    private void detectContextLoss(BlackboardEntry entry) {
        String agentId = entry.getAgentId();
        
        // Only check for CODE and TASK_BREAKDOWN entries
        if (!("CODE".equals(entry.getEntryType()) || "TASK_BREAKDOWN".equals(entry.getEntryType()))) {
            return;
        }

        Instant lastQuery = lastMemoryQuery.get(agentId);
        Instant entryTime = entry.getTimestamp();

        // Check if agent queried memory within last 60 seconds
        if (lastQuery == null || lastQuery.plusSeconds(60).isBefore(entryTime)) {
            String evidence = String.format(
                "Agent %s posted %s '%s' without querying memory for context. " +
                "Last memory query: %s. This may lead to context loss and inconsistent decisions.",
                agentId, entry.getEntryType(), entry.getTitle(),
                lastQuery != null ? lastQuery.toString() : "never"
            );
            
            MASTViolation violation = new MASTViolation(
                agentId,
                MASTFailureMode.FM_1_4_CONTEXT_LOSS,
                String.valueOf(entry.getId()),
                evidence
            );

            violationRepository.save(violation);
            log.warn("Detected FM-1.4 Context Loss: Agent {} posted without memory query", agentId);
        }
    }

    /**
     * FM-1.2: Ambiguous Language Detection
     * SRS or requirements contain ambiguous language
     */
    private void detectAmbiguousLanguage(BlackboardEntry entry) {
        // Only check SRS and TASK_BREAKDOWN entries
        if (!("SRS".equals(entry.getEntryType()) || "TASK_BREAKDOWN".equals(entry.getEntryType()))) {
            return;
        }

        String content = entry.getContent();
        if (content == null) {
            return;
        }

        // Ambiguous patterns
        String[] ambiguousPatterns = {
            "maybe", "possibly", "if needed", "TBD", "TODO", "probably",
            "might", "could be", "should be", "may be", "unclear",
            "not sure", "to be determined"
        };

        List<String> foundPatterns = new ArrayList<>();
        String lowerContent = content.toLowerCase();
        
        for (String pattern : ambiguousPatterns) {
            if (lowerContent.contains(pattern)) {
                foundPatterns.add(pattern);
            }
        }

        if (!foundPatterns.isEmpty()) {
            String evidence = String.format(
                "Entry '%s' contains ambiguous language: %s. " +
                "This may lead to misinterpretation and incorrect implementation.",
                entry.getTitle(), String.join(", ", foundPatterns)
            );
            
            MASTViolation violation = new MASTViolation(
                entry.getAgentId(),
                MASTFailureMode.FM_1_1_SPECIFICATION_VIOLATION,
                String.valueOf(entry.getId()),
                evidence
            );

            violationRepository.save(violation);
            log.warn("Detected FM-1.1 Ambiguous Language: Entry {} contains: {}", 
                entry.getTitle(), foundPatterns);
        }
    }

    /**
     * FM-3.1: Test Coverage Below Threshold
     * Parse test results and check if coverage < 80%
     */
    private void detectLowTestCoverage(BlackboardEntry entry) {
        String content = entry.getContent();
        if (content == null) {
            return;
        }

        // Parse coverage from content (assumes format: "Coverage: XX%")
        int coverage = parseCoverage(content);
        
        if (coverage >= 0 && coverage < 80) {
            String evidence = String.format(
                "Test coverage for '%s' is %d%%, below 80%% threshold. " +
                "Insufficient test coverage increases risk of bugs.",
                entry.getTitle(), coverage
            );
            
            MASTViolation violation = new MASTViolation(
                entry.getAgentId(),
                MASTFailureMode.FM_3_1_OUTPUT_QUALITY,
                String.valueOf(entry.getId()),
                evidence
            );

            violationRepository.save(violation);
            log.warn("Detected FM-3.1 Low Test Coverage: {} has {}% coverage", 
                entry.getTitle(), coverage);
        }
    }

    /**
     * FM-2.2: Duplicate Work Detection
     * Multiple agents working on similar tasks
     */
    private void detectDuplicateWork(BlackboardEntry entry) {
        if (!"CODE".equals(entry.getEntryType())) {
            return;
        }

        // Check recent entries for similar titles
        for (BlackboardEntry other : recentEntries.values()) {
            if (Objects.equals(other.getId(), entry.getId())) {
                continue; // Skip self
            }
            
            if (!"CODE".equals(other.getEntryType())) {
                continue;
            }

            if (Objects.equals(other.getAgentId(), entry.getAgentId())) {
                continue; // Same agent is OK
            }

            // Simple similarity: check if titles are very similar
            double similarity = calculateTitleSimilarity(entry.getTitle(), other.getTitle());
            
            if (similarity > 0.7) { // 70% similarity threshold
                String evidence = String.format(
                    "Agent %s is working on '%s' which is %.0f%% similar to '%s' by agent %s. " +
                    "This indicates potential duplicate work.",
                    entry.getAgentId(), entry.getTitle(),
                    similarity * 100,
                    other.getTitle(), other.getAgentId()
                );
                
                MASTViolation violation = new MASTViolation(
                    entry.getAgentId(),
                    MASTFailureMode.FM_2_2_COMMUNICATION_BREAKDOWN,
                    String.valueOf(entry.getId()),
                    evidence
                );

                violationRepository.save(violation);
                log.warn("Detected FM-2.2 Duplicate Work: '{}' similar to '{}'", 
                    entry.getTitle(), other.getTitle());
                break; // Only report one duplicate
            }
        }
    }

    /**
     * FM-3.2: Unresolved Code Review Issues
     * Reviewer marks code as REQUIRES_CHANGES but no follow-up
     */
    private void detectUnresolvedReview(BlackboardEntry review) {
        String content = review.getContent();
        if (content == null || !content.contains("REQUIRES_CHANGES")) {
            return; // Review passed or no action needed
        }

        // Schedule check for 30 minutes later (in real implementation, use scheduled task)
        // For now, just create a violation immediately
        String evidence = String.format(
            "Code review '%s' marked as REQUIRES_CHANGES. " +
            "Monitoring for follow-up action from original author.",
            review.getTitle()
        );
        
        MASTViolation violation = new MASTViolation(
            review.getAgentId(),
            MASTFailureMode.FM_3_2_INCOMPLETE_OUTPUT,
            String.valueOf(review.getId()),
            evidence
        );

        violationRepository.save(violation);
        log.info("Detected FM-3.2 Unresolved Review: Monitoring '{}'", review.getTitle());
    }

    // Helper methods

    private int parseCoverage(String content) {
        try {
            // Look for patterns like "Coverage: 72%" or "72% coverage"
            String pattern = "([0-9]+)\\s*%";
            java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern, 
                java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = regex.matcher(content);
            
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            log.debug("Could not parse coverage from content", e);
        }
        return -1; // Not found
    }

    private double calculateTitleSimilarity(String title1, String title2) {
        if (title1 == null || title2 == null) {
            return 0.0;
        }

        // Simple Jaccard similarity on words
        Set<String> words1 = new HashSet<>(Arrays.asList(
            title1.toLowerCase().split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(
            title2.toLowerCase().split("\\s+")));

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
}
