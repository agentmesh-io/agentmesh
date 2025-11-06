package com.therighthandapp.agentmesh.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {

    List<Project> findByTenantId(String tenantId);

    Optional<Project> findByProjectKey(String projectKey);

    Optional<Project> findByTenantIdAndProjectKey(String tenantId, String projectKey);

    List<Project> findByTenantIdAndStatus(String tenantId, Project.ProjectStatus status);

    long countByTenantId(String tenantId);
}

