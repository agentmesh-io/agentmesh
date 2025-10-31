package com.therighthandapp.agentmesh.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {

    Optional<Tenant> findByOrganizationId(String organizationId);

    Optional<Tenant> findByK8sNamespace(String k8sNamespace);

    boolean existsByOrganizationId(String organizationId);
}

