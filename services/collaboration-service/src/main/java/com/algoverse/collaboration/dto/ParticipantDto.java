package com.algoverse.collaboration.dto;

import com.algoverse.collaboration.domain.RoomParticipant;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ParticipantDto {
    private UUID userId;
    private RoomParticipant.ParticipantRole role;
    private Instant joinedAt;
    private boolean online;
}
