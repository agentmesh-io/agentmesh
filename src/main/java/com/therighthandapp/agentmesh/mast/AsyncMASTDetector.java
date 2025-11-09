package com.therighthandapp.agentmesh.mast;

import com.therighthandapp.agentmesh.blackboard.BlackboardEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous MAST violation detection service.
 * 
 * Wraps the synchronous MASTDetector to perform non-blocking detection.
 * This prevents MAST detection from blocking the main application thread
 * during high-load scenarios with 100+ concurrent agents.
 * 
 * Benefits:
 * - Non-blocking: Blackboard posts don't wait for MAST analysis
 * - Scalable: Can process many detections concurrently
 * - Resilient: Detection failures don't impact agent execution
 * - Observable: Uses CompletableFuture for monitoring and chaining
 * 
 * Usage:
 * <pre>
 * asyncDetector.analyzeAsync(entry)
 *     .thenAccept(v -> log.info("Analysis complete"))
 *     .exceptionally(ex -> { log.error("Detection failed", ex); return null; });
 * </pre>
 */
@Service
public class AsyncMASTDetector {

    private static final Logger logger = LoggerFactory.getLogger(AsyncMASTDetector.class);

    private final MASTDetector detector;
    private final MASTViolationService violationService;

    @Autowired
    public AsyncMASTDetector(MASTDetector detector, MASTViolationService violationService) {
        this.detector = detector;
        this.violationService = violationService;
    }

    /**
     * Asynchronously analyze a blackboard entry for MAST violations.
     * 
     * @param entry The blackboard entry to analyze
     * @return CompletableFuture that completes when analysis is done
     */
    @Async("mastExecutor")
    public CompletableFuture<Void> analyzeAsync(BlackboardEntry entry) {
        
        return CompletableFuture.runAsync(() -> {
            try {
                logger.debug("Starting async MAST analysis for entry: {}, agent: {}",
                        entry.getId(), entry.getAgentId());

                long startTime = System.currentTimeMillis();

                // Run comprehensive MAST analysis (all 14 modes)
                detector.analyzeBlackboardEntry(entry);

                long duration = System.currentTimeMillis() - startTime;
                logger.debug("Async MAST analysis completed in {}ms for agent: {}",
                        duration, entry.getAgentId());

            } catch (Exception e) {
                logger.error("Async MAST analysis failed for entry: {}, agent: {}",
                        entry.getId(), entry.getAgentId(), e);
                throw new MASTDetectionException("Async analysis failed", e);
            }
        });
    }

    /**
     * Asynchronously get all violations for an agent.
     * 
     * @param agentId The agent ID
     * @return CompletableFuture containing list of violations
     */
    @Async("mastExecutor")
    public CompletableFuture<List<MASTViolation>> getViolationsAsync(String agentId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Fetching violations for agent: {}", agentId);
                return violationService.findByAgentId(agentId);
            } catch (Exception e) {
                logger.error("Failed to fetch violations for agent: {}", agentId, e);
                throw new MASTDetectionException("Violation fetch failed", e);
            }
        });
    }

    /**
     * Asynchronously get unresolved violations.
     * 
     * @return CompletableFuture containing list of unresolved violations
     */
    @Async("mastExecutor")
    public CompletableFuture<List<MASTViolation>> getUnresolvedAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Fetching unresolved violations");
                return violationService.findUnresolved();
            } catch (Exception e) {
                logger.error("Failed to fetch unresolved violations", e);
                throw new MASTDetectionException("Unresolved fetch failed", e);
            }
        });
    }

    /**
     * Fire-and-forget analysis (no result needed).
     * Useful when you just want to trigger detection without waiting.
     * 
     * @param entry The blackboard entry to analyze
     */
    public void analyzeFireAndForget(BlackboardEntry entry) {
        analyzeAsync(entry)
                .exceptionally(ex -> {
                    logger.error("Fire-and-forget analysis failed for entry: {}, agent: {}",
                            entry.getId(), entry.getAgentId(), ex);
                    return null;
                });
    }

    /**
     * Batch analyze multiple entries asynchronously.
     * 
     * @param entries List of entries to analyze
     * @return CompletableFuture that completes when all analyses are done
     */
    public CompletableFuture<Void> analyzeBatchAsync(List<BlackboardEntry> entries) {
        List<CompletableFuture<Void>> futures = entries.stream()
                .map(this::analyzeAsync)
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.error("Batch analysis had failures", ex);
                    } else {
                        logger.info("Batch analysis complete for {} entries", entries.size());
                    }
                });
    }

    /**
     * Custom exception for MAST detection failures.
     */
    public static class MASTDetectionException extends RuntimeException {
        public MASTDetectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
