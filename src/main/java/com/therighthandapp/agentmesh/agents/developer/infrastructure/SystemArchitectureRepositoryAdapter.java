package com.therighthandapp.agentmesh.agents.developer.infrastructure;

import com.therighthandapp.agentmesh.agents.architect.domain.SystemArchitecture;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Infrastructure Adapter: Adapts ArchitectureRepository for DeveloperAgent
 */
@RequiredArgsConstructor
public class SystemArchitectureRepositoryAdapter implements com.therighthandapp.agentmesh.agents.developer.ports.ArchitectureRepository {
    
    private final com.therighthandapp.agentmesh.agents.architect.ports.ArchitectureRepository architectureRepository;
    
    @Override
    public Optional<SystemArchitecture> findById(String architectureId) {
        SystemArchitecture architecture = architectureRepository.findById(architectureId);
        return Optional.ofNullable(architecture);
    }
    
    @Override
    public Optional<SystemArchitecture> findLatestByPlanId(String planId) {
        SystemArchitecture architecture = architectureRepository.findLatestByPlanId(planId);
        return Optional.ofNullable(architecture);
    }
}
