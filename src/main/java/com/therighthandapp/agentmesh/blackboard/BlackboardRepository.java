package com.therighthandapp.agentmesh.blackboard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BlackboardRepository extends JpaRepository<BlackboardEntry, Long> {
    List<BlackboardEntry> findByAgentId(String agentId);

    List<BlackboardEntry> findByEntryType(String entryType);

    @Query("SELECT b FROM BlackboardEntry b ORDER BY b.timestamp DESC")
    List<BlackboardEntry> findAllOrderedByTimestamp();

    @Query("SELECT b FROM BlackboardEntry b WHERE b.entryType = :entryType ORDER BY b.timestamp DESC")
    List<BlackboardEntry> findByEntryTypeOrderedByTimestamp(String entryType);
}

