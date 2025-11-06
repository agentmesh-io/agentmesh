package com.therighthandapp.agentmesh.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.therighthandapp.agentmesh.events.EventPublisher;
import com.therighthandapp.agentmesh.events.ProjectInitializedEvent;
import com.therighthandapp.agentmesh.github.GitHubIntegrationService;
import com.therighthandapp.agentmesh.integration.dto.SrsHandoffDto;
import com.therighthandapp.agentmesh.orchestration.TemporalWorkflowService;
import com.therighthandapp.agentmesh.tenant.Project;
import com.therighthandapp.agentmesh.tenant.Project.ProjectStatus;
import com.therighthandapp.agentmesh.tenant.ProjectRepository;
import com.therighthandapp.agentmesh.tenant.Tenant;
import com.therighthandapp.agentmesh.tenant.Tenant.TenantTier;
import com.therighthandapp.agentmesh.tenant.TenantRepository;
import com.therighthandapp.agentmesh.tenant.TenantService;
import com.therighthandapp.agentmesh.tenant.TenantService.CreateTenantRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service responsible for initializing new projects from Auto-BADS SRS events.
 * Handles tenant/project creation, GitHub repository setup, and workflow initiation.
 */
@Service
public class ProjectInitializationService {
    
    private static final Logger log = LoggerFactory.getLogger(ProjectInitializationService.class);
    
    private static final String DEFAULT_TENANT_ID = "autobads-default";
    private static final String DEFAULT_TENANT_NAME = "Auto-BADS Default Tenant";
    
    private final TenantService tenantService;
    private final TenantRepository tenantRepository;
    private final ProjectRepository projectRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    
    @Autowired(required = false)
    private GitHubIntegrationService gitHubService;
    
    @Autowired(required = false)
    private TemporalWorkflowService temporalWorkflowService;
    
    @Value("${agentmesh.integration.auto-create-tenant:true}")
    private boolean autoCreateTenant;
    
    @Value("${agentmesh.github.enabled:false}")
    private boolean githubEnabled;
    
    @Value("${agentmesh.temporal.enabled:false}")
    private boolean temporalEnabled;
    
    public ProjectInitializationService(
            TenantService tenantService,
            TenantRepository tenantRepository,
            ProjectRepository projectRepository,
            EventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.tenantService = tenantService;
        this.tenantRepository = tenantRepository;
        this.projectRepository = projectRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Initialize a new project from SRS data received from Auto-BADS
     */
    @Transactional
    public ProjectInitializationResult initializeProject(SrsHandoffDto srsData, String correlationId) {
        log.info("Initializing project from SRS: ideaTitle={}, correlationId={}", 
                 srsData.getIdeaTitle(), correlationId);
        
        try {
            // Step 1: Ensure tenant exists
            Tenant tenant = getOrCreateDefaultTenant();
            log.info("Using tenant: {} (id={})", tenant.getName(), tenant.getId());
            
            // Step 2: Create project
            Project project = createProject(tenant, srsData);
            log.info("Created project: {} (id={}, key={})", 
                     project.getName(), project.getId(), project.getProjectKey());
            
            // Step 3: Initialize GitHub repository (if enabled)
            String githubRepoUrl = null;
            if (githubEnabled && gitHubService != null) {
                try {
                    githubRepoUrl = initializeGitHubRepository(project, srsData);
                    log.info("Created GitHub repository: {}", githubRepoUrl);
                } catch (Exception e) {
                    log.warn("Failed to create GitHub repository, continuing without it", e);
                }
            } else {
                log.info("GitHub integration disabled, skipping repository creation");
            }
            
            // Step 4: Prepare project metadata
            Map<String, Object> projectMetadata = buildProjectMetadata(srsData, githubRepoUrl);
            
            // Step 5: Publish ProjectInitializedEvent to Kafka
            publishProjectInitializedEvent(project, srsData, correlationId, githubRepoUrl);
            
            // Step 6: Start Temporal workflow for SDLC
            if (temporalEnabled && temporalWorkflowService != null) {
                try {
                    String workflowId = temporalWorkflowService.startSdlcWorkflow(
                        project.getId(),
                        project.getProjectKey(),
                        srsData
                    );
                    
                    if (workflowId != null) {
                        project.setWorkflowId(workflowId);
                        projectRepository.save(project);
                        log.info("Started Temporal workflow for project: projectId={}, workflowId={}", 
                            project.getId(), workflowId);
                    } else {
                        log.warn("Temporal workflow ID is null for project: {}", project.getId());
                    }
                } catch (Exception e) {
                    log.error("Failed to start Temporal workflow for project: {}", project.getId(), e);
                    // Non-fatal: project can exist without workflow
                }
            } else {
                log.info("Temporal integration disabled, skipping workflow start for project: {}", project.getId());
            }
            
            log.info("Project initialization completed successfully: projectId={}", project.getId());
            
            return ProjectInitializationResult.success(
                    project.getId(),
                    project.getProjectKey(),
                    tenant.getId(),
                    githubRepoUrl,
                    correlationId,
                    projectMetadata
            );
            
        } catch (Exception e) {
            log.error("Failed to initialize project from SRS. correlationId={}, error={}", 
                      correlationId, e.getMessage(), e);
            
            return ProjectInitializationResult.failure(correlationId, e.getMessage());
        }
    }
    
    /**
     * Get or create the default tenant for Auto-BADS projects
     */
    private Tenant getOrCreateDefaultTenant() {
        Optional<Tenant> existingTenant = tenantRepository.findByOrganizationId(DEFAULT_TENANT_ID);
        
        if (existingTenant.isPresent()) {
            return existingTenant.get();
        }
        
        if (!autoCreateTenant) {
            throw new IllegalStateException("Default tenant does not exist and auto-creation is disabled");
        }
        
        log.info("Creating default tenant for Auto-BADS integration: {}", DEFAULT_TENANT_ID);
        
        CreateTenantRequest request = new CreateTenantRequest();
        request.setName(DEFAULT_TENANT_NAME);
        request.setOrganizationId(DEFAULT_TENANT_ID);
        request.setTier(TenantTier.PREMIUM); // Default to PREMIUM for Auto-BADS projects
        request.setDataRegion("us-east-1");
        request.setRequiresDataLocality(false);
        
        return tenantService.createTenant(request);
    }
    
    /**
     * Create a new project entity from SRS data
     */
    private Project createProject(Tenant tenant, SrsHandoffDto srsData) {
        // Generate unique project key from idea title
        String projectKey = generateProjectKey(srsData.getIdeaTitle());
        
        // Check if project with this key already exists
        Optional<Project> existing = projectRepository.findByProjectKey(projectKey);
        if (existing.isPresent()) {
            // Append random suffix to make it unique
            projectKey = projectKey + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        }
        
        Project project = new Project();
        project.setTenant(tenant);
        project.setName(srsData.getIdeaTitle());
        project.setProjectKey(projectKey);
        project.setDescription(srsData.getProblemStatement());
        project.setStatus(ProjectStatus.ACTIVE);
        project.setTrackCosts(true);
        
        // Set resource limits based on SRS complexity
        project.setMaxAgents(estimateMaxAgents(srsData));
        project.setMaxStorageMb(estimateMaxStorage(srsData));
        
        return projectRepository.save(project);
    }
    
    /**
     * Generate a project key from idea title (e.g., "E-Commerce Platform" -> "ECOM")
     */
    private String generateProjectKey(String ideaTitle) {
        if (ideaTitle == null || ideaTitle.isBlank()) {
            return "PROJ";
        }
        
        // Extract first letters of each word, up to 4 characters
        String[] words = ideaTitle.trim().split("\\s+");
        StringBuilder key = new StringBuilder();
        
        for (String word : words) {
            if (key.length() >= 4) break;
            if (!word.isEmpty() && Character.isLetterOrDigit(word.charAt(0))) {
                key.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        
        // If key is too short, pad with first letters from first word
        if (key.length() < 2 && !words[0].isEmpty()) {
            String firstWord = words[0].toUpperCase().replaceAll("[^A-Z0-9]", "");
            key.append(firstWord.substring(0, Math.min(4 - key.length(), firstWord.length())));
        }
        
        return key.length() > 0 ? key.toString() : "PROJ";
    }
    
    /**
     * Estimate required number of agents based on SRS complexity
     */
    private Integer estimateMaxAgents(SrsHandoffDto srsData) {
        int baseAgents = 5; // Planner, Coder, Reviewer, Tester, Documenter
        
        // Add agents based on functional requirements
        if (srsData.getSrs() != null && srsData.getSrs().getFunctionalRequirements() != null) {
            int requirements = srsData.getSrs().getFunctionalRequirements().size();
            if (requirements > 20) baseAgents += 3;
            else if (requirements > 10) baseAgents += 2;
            else if (requirements > 5) baseAgents += 1;
        }
        
        return baseAgents;
    }
    
    /**
     * Estimate required storage based on project scope
     */
    private Long estimateMaxStorage(SrsHandoffDto srsData) {
        long baseStorageMb = 1024L; // 1 GB base
        
        // Add storage based on dependencies and complexity
        if (srsData.getSrs() != null && srsData.getSrs().getDependencies() != null) {
            int dependencies = srsData.getSrs().getDependencies().size();
            baseStorageMb += dependencies * 100L; // 100 MB per dependency
        }
        
        // Add storage for backlog items
        if (srsData.getPrioritizedBacklog() != null) {
            int features = srsData.getPrioritizedBacklog().size();
            baseStorageMb += features * 50L; // 50 MB per feature
        }
        
        return Math.min(baseStorageMb, 10240L); // Cap at 10 GB
    }
    
    /**
     * Initialize GitHub repository for the project
     */
    private String initializeGitHubRepository(Project project, SrsHandoffDto srsData) {
        if (gitHubService == null) {
            log.warn("GitHub service not available, skipping repository creation");
            return null;
        }
        
        try {
            log.info("Initializing GitHub repository for project: {}", project.getProjectKey());
            
            // Generate SRS markdown content
            String srsContent = generateSrsMarkdown(srsData);
            
            // Create repository via GitHubIntegrationService
            String repoUrl = gitHubService.createProjectRepository(
                project.getName(),
                project.getProjectKey(),
                srsData.getBusinessCase(),
                srsContent
            );
            
            if (repoUrl != null) {
                log.info("Successfully created GitHub repository: {}", repoUrl);
            } else {
                log.warn("GitHub repository creation returned null for project: {}", project.getProjectKey());
            }
            
            return repoUrl;
            
        } catch (Exception e) {
            log.error("Failed to initialize GitHub repository for project: {}", project.getProjectKey(), e);
            // Non-fatal: project can exist without GitHub repo
            return null;
        }
    }
    
    /**
     * Generate SRS content in Markdown format
     */
    private String generateSrsMarkdown(SrsHandoffDto srsData) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("# Software Requirements Specification\n\n");
        sb.append("**Project**: ").append(srsData.getIdeaTitle()).append("\n\n");
        sb.append("**Generated**: ").append(srsData.getGeneratedAt()).append("\n\n");
        
        // Business Context
        sb.append("## Business Context\n\n");
        if (srsData.getBusinessCase() != null) {
            sb.append("### Business Case\n").append(srsData.getBusinessCase()).append("\n\n");
        }
        if (srsData.getProblemStatement() != null) {
            sb.append("### Problem Statement\n").append(srsData.getProblemStatement()).append("\n\n");
        }
        if (srsData.getStrategicAlignment() != null) {
            sb.append("### Strategic Alignment\n").append(srsData.getStrategicAlignment()).append("\n\n");
        }
        
        // Requirements
        if (srsData.getSrs() != null) {
            SrsHandoffDto.SoftwareRequirementsSpecification srs = srsData.getSrs();
            
            sb.append("## Requirements\n\n");
            
            // Functional Requirements
            if (srs.getFunctionalRequirements() != null && !srs.getFunctionalRequirements().isEmpty()) {
                sb.append("### Functional Requirements\n\n");
                for (SrsHandoffDto.FunctionalRequirement req : srs.getFunctionalRequirements()) {
                    sb.append("- **").append(req.getId()).append("**: ").append(req.getRequirement()).append("\n");
                    if (req.getRationale() != null) {
                        sb.append("  - *Rationale*: ").append(req.getRationale()).append("\n");
                    }
                }
                sb.append("\n");
            }
            
            // Non-Functional Requirements
            if (srs.getNonFunctionalRequirements() != null && !srs.getNonFunctionalRequirements().isEmpty()) {
                sb.append("### Non-Functional Requirements\n\n");
                for (SrsHandoffDto.NonFunctionalRequirement req : srs.getNonFunctionalRequirements()) {
                    sb.append("- **").append(req.getCategory()).append("**: ").append(req.getRequirement()).append("\n");
                    if (req.getMetric() != null && req.getTargetValue() != null) {
                        sb.append("  - *Target*: ").append(req.getMetric()).append(" = ").append(req.getTargetValue()).append("\n");
                    }
                }
                sb.append("\n");
            }
            
            // Architecture
            if (srs.getArchitecture() != null) {
                sb.append("### System Architecture\n\n");
                SrsHandoffDto.SystemArchitecture arch = srs.getArchitecture();
                if (arch.getArchitectureStyle() != null) {
                    sb.append("**Style**: ").append(arch.getArchitectureStyle()).append("\n\n");
                }
                if (arch.getComponents() != null && !arch.getComponents().isEmpty()) {
                    sb.append("**Components**:\n");
                    arch.getComponents().forEach(c -> sb.append("- ").append(c).append("\n"));
                    sb.append("\n");
                }
            }
        }
        
        // Recommended Solution
        if (srsData.getRecommendedSolutionType() != null) {
            sb.append("## Recommended Solution\n\n");
            sb.append("**Type**: ").append(srsData.getRecommendedSolutionType()).append("\n");
            if (srsData.getRecommendationScore() != null) {
                sb.append("**Confidence Score**: ").append(String.format("%.2f", srsData.getRecommendationScore())).append("\n\n");
            }
        }
        
        // Risk Assessment
        if (srsData.getRiskAssessment() != null) {
            sb.append("## Risk Assessment\n\n");
            SrsHandoffDto.RiskAssessment risk = srsData.getRiskAssessment();
            if (risk.getOverallRiskLevel() != null) {
                sb.append("**Overall Risk Level**: ").append(risk.getOverallRiskLevel()).append("\n\n");
            }
            if (risk.getIdentifiedRisks() != null && !risk.getIdentifiedRisks().isEmpty()) {
                sb.append("### Identified Risks\n\n");
                for (SrsHandoffDto.Risk r : risk.getIdentifiedRisks()) {
                    sb.append("- **").append(r.getCategory()).append("** (").append(r.getSeverity()).append("): ");
                    sb.append(r.getDescription()).append("\n");
                }
            }
        }
        
        sb.append("\n---\n\n");
        sb.append("*This SRS was automatically generated by Auto-BADS and processed by AgentMesh*\n");
        
        return sb.toString();
    }
    
    /**
     * Build project metadata from SRS data
     */
    private Map<String, Object> buildProjectMetadata(SrsHandoffDto srsData, String githubRepoUrl) {
        Map<String, Object> metadata = new HashMap<>();
        
        metadata.put("ideaId", srsData.getIdeaId() != null ? srsData.getIdeaId().toString() : null);
        metadata.put("businessCase", srsData.getBusinessCase());
        metadata.put("strategicAlignment", srsData.getStrategicAlignment());
        metadata.put("recommendedSolutionType", srsData.getRecommendedSolutionType());
        metadata.put("recommendationScore", srsData.getRecommendationScore());
        metadata.put("githubRepoUrl", githubRepoUrl);
        metadata.put("srsVersion", srsData.getSrs() != null ? srsData.getSrs().getVersion() : null);
        metadata.put("generatedAt", srsData.getGeneratedAt());
        
        // Add financial projections
        if (srsData.getFinancials() != null) {
            Map<String, Object> financials = new HashMap<>();
            financials.put("totalCostOfOwnership", srsData.getFinancials().getTotalCostOfOwnership());
            financials.put("expectedRoi", srsData.getFinancials().getExpectedRoi());
            financials.put("breakEvenMonths", srsData.getFinancials().getBreakEvenMonths());
            metadata.put("financials", financials);
        }
        
        // Add risk assessment
        if (srsData.getRiskAssessment() != null) {
            metadata.put("overallRiskLevel", srsData.getRiskAssessment().getOverallRiskLevel());
            metadata.put("identifiedRisksCount", 
                        srsData.getRiskAssessment().getIdentifiedRisks() != null 
                            ? srsData.getRiskAssessment().getIdentifiedRisks().size() 
                            : 0);
        }
        
        return metadata;
    }
    
    /**
     * Publish ProjectInitializedEvent to Kafka
     */
    private void publishProjectInitializedEvent(Project project, SrsHandoffDto srsData, 
                                               String correlationId, String githubRepoUrl) {
        try {
            ProjectInitializedEvent event = new ProjectInitializedEvent(
                    project.getId(),
                    project.getProjectKey(),
                    project.getName(),
                    project.getTenant().getId(),
                    srsData.getIdeaId() != null ? srsData.getIdeaId().toString() : null,
                    githubRepoUrl,
                    LocalDateTime.now(),
                    correlationId
            );
            
            eventPublisher.publishProjectInitialized(event);
            log.info("Published ProjectInitializedEvent: projectId={}, correlationId={}", 
                     project.getId(), correlationId);
            
        } catch (Exception e) {
            log.error("Failed to publish ProjectInitializedEvent", e);
            // Don't throw - project is already created, just log the error
        }
    }
}
