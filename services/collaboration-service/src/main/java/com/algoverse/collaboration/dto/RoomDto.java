package com.algoverse.collaboration.dto;

import com.algoverse.collaboration.domain.Room;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class RoomDto {
    private UUID id;
    private String code;
    private Room.RoomType type;
    private UUID problemId;
    private UUID hostId;
    private Room.RoomStatus status;
    private int maxParticipants;
    private Map<String, Object> settings;
    private Instant startedAt;
    private Instant endedAt;
    private Instant createdAt;
    private int participantCount;
    private List<ParticipantDto> participants;
}
