package com.algoverse.collaboration.repository;

import com.algoverse.collaboration.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    Optional<Room> findByCode(String code);

    List<Room> findByHostIdOrderByCreatedAtDesc(UUID hostId);

    @Query("SELECT r FROM Room r WHERE r.status = 'ACTIVE' ORDER BY r.createdAt DESC")
    List<Room> findActiveRooms();
}
