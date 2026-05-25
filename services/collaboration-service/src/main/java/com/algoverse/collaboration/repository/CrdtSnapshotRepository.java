package com.algoverse.collaboration.repository;

import com.algoverse.collaboration.domain.CrdtSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CrdtSnapshotRepository extends JpaRepository<CrdtSnapshot, UUID> {

    Optional<CrdtSnapshot> findTopByRoomIdOrderByVersionDesc(UUID roomId);
}
