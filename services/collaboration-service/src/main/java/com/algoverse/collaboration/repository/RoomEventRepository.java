package com.algoverse.collaboration.repository;

import com.algoverse.collaboration.domain.RoomEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomEventRepository extends JpaRepository<RoomEvent, UUID> {

    List<RoomEvent> findByRoomIdOrderByCreatedAtAsc(UUID roomId);

    List<RoomEvent> findTop200ByRoomIdOrderByCreatedAtAsc(UUID roomId);
}
