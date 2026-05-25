package com.algoverse.collaboration.repository;

import com.algoverse.collaboration.domain.RoomParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomParticipantRepository extends JpaRepository<RoomParticipant, UUID> {

    @Query("SELECT p FROM RoomParticipant p WHERE p.roomId = :roomId AND p.leftAt IS NULL")
    List<RoomParticipant> findActiveByRoomId(UUID roomId);

    Optional<RoomParticipant> findByRoomIdAndUserId(UUID roomId, UUID userId);

    long countByRoomIdAndLeftAtIsNull(UUID roomId);
}
