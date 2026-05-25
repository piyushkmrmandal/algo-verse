package com.algoverse.collaboration.service;

import com.algoverse.collaboration.domain.*;
import com.algoverse.collaboration.dto.*;
import com.algoverse.collaboration.kafka.CollabEventProducer;
import com.algoverse.collaboration.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepo;
    private final RoomParticipantRepository participantRepo;
    private final RoomEventRepository eventRepo;
    private final CrdtSnapshotRepository snapshotRepo;
    private final PresenceService presenceService;
    private final DocumentStateService documentService;
    private final CollabEventProducer eventProducer;

    @Transactional
    public RoomDto createRoom(CreateRoomRequest req, UUID hostId) {
        String code = generateUniqueCode();

        Room room = Room.builder()
                .code(code)
                .type(req.getType())
                .problemId(req.getProblemId())
                .hostId(hostId)
                .maxParticipants(req.getMaxParticipants())
                .settings(req.getSettings())
                .build();
        room = roomRepo.save(room);

        // Host is auto-added as HOST participant
        participantRepo.save(RoomParticipant.builder()
                .roomId(room.getId())
                .userId(hostId)
                .role(RoomParticipant.ParticipantRole.HOST)
                .build());

        documentService.initDocument(room.getId(), "");
        logEvent(room.getId(), hostId, "ROOM_CREATED", null);
        eventProducer.sendRoomCreated(room.getId(), hostId, room.getType().name());

        log.info("Room created: {} ({})", code, room.getId());
        return toDto(room);
    }

    @Transactional
    public RoomDto joinRoom(String code, UUID userId) {
        Room room = roomRepo.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("Room not found: " + code));

        if (room.getStatus() == Room.RoomStatus.ENDED) {
            throw new IllegalStateException("Room has ended");
        }

        long currentCount = participantRepo.countByRoomIdAndLeftAtIsNull(room.getId());
        if (currentCount >= room.getMaxParticipants()) {
            throw new IllegalStateException("Room is full");
        }

        // Re-join or new join
        participantRepo.findByRoomIdAndUserId(room.getId(), userId)
                .ifPresentOrElse(
                        p -> p.setLeftAt(null),
                        () -> participantRepo.save(RoomParticipant.builder()
                                .roomId(room.getId())
                                .userId(userId)
                                .role(RoomParticipant.ParticipantRole.OBSERVER)
                                .build())
                );

        if (room.getStatus() == Room.RoomStatus.WAITING) {
            room.setStatus(Room.RoomStatus.ACTIVE);
            room.setStartedAt(Instant.now());
            roomRepo.save(room);
        }

        presenceService.markOnline(room.getId(), userId);
        logEvent(room.getId(), userId, "USER_JOINED", null);

        return toDto(room);
    }

    @Transactional
    public void leaveRoom(UUID roomId, UUID userId) {
        participantRepo.findByRoomIdAndUserId(roomId, userId)
                .ifPresent(p -> p.setLeftAt(Instant.now()));

        presenceService.markOffline(roomId, userId);
        logEvent(roomId, userId, "USER_LEFT", null);

        // End room if host leaves or everyone left
        Room room = roomRepo.findById(roomId).orElse(null);
        if (room == null || room.getStatus() == Room.RoomStatus.ENDED) return;

        long remaining = participantRepo.countByRoomIdAndLeftAtIsNull(roomId);
        boolean hostLeft = userId.equals(room.getHostId());

        if (remaining == 0 || hostLeft) {
            endRoom(room);
        }
    }

    @Transactional
    public RoomDto getRoomById(UUID roomId) {
        return toDto(roomRepo.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("Room not found")));
    }

    @Transactional(readOnly = true)
    public List<RoomDto> getMyRooms(UUID userId) {
        return roomRepo.findByHostIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public void endRoom(Room room) {
        room.setStatus(Room.RoomStatus.ENDED);
        room.setEndedAt(Instant.now());
        roomRepo.save(room);

        // Persist final document as snapshot
        String content = documentService.getContent(room.getId());
        long version = documentService.getVersion(room.getId());
        snapshotRepo.save(CrdtSnapshot.builder()
                .roomId(room.getId())
                .snapshotData(content.getBytes())
                .version(version)
                .build());

        documentService.clearDocument(room.getId());
        logEvent(room.getId(), null, "ROOM_ENDED", null);
        eventProducer.sendRoomEnded(room.getId(), room.getHostId(), room.getType().name());
        log.info("Room ended: {} ({})", room.getCode(), room.getId());
    }

    private void logEvent(UUID roomId, UUID userId, String type, Object payload) {
        eventRepo.save(RoomEvent.builder()
                .roomId(roomId)
                .userId(userId)
                .eventType(type)
                .build());
    }

    private RoomDto toDto(Room room) {
        List<RoomParticipant> participants = participantRepo.findActiveByRoomId(room.getId());
        Set<Object> online = presenceService.getOnlineUsers(room.getId());

        List<ParticipantDto> participantDtos = participants.stream()
                .map(p -> ParticipantDto.builder()
                        .userId(p.getUserId())
                        .role(p.getRole())
                        .joinedAt(p.getJoinedAt())
                        .online(online.contains(p.getUserId().toString()))
                        .build())
                .toList();

        return RoomDto.builder()
                .id(room.getId())
                .code(room.getCode())
                .type(room.getType())
                .problemId(room.getProblemId())
                .hostId(room.getHostId())
                .status(room.getStatus())
                .maxParticipants(room.getMaxParticipants())
                .settings(room.getSettings())
                .startedAt(room.getStartedAt())
                .endedAt(room.getEndedAt())
                .createdAt(room.getCreatedAt())
                .participantCount(participantDtos.size())
                .participants(participantDtos)
                .build();
    }

    private String generateUniqueCode() {
        String code;
        int attempts = 0;
        do {
            String part1 = RandomStringUtils.random(3, "ABCDEFGHJKLMNPQRSTUVWXYZ23456789");
            String part2 = RandomStringUtils.random(3, "ABCDEFGHJKLMNPQRSTUVWXYZ23456789");
            String part3 = RandomStringUtils.random(3, "ABCDEFGHJKLMNPQRSTUVWXYZ23456789");
            code = part1 + "-" + part2 + "-" + part3;
            attempts++;
            if (attempts > 100) throw new IllegalStateException("Cannot generate unique room code");
        } while (roomRepo.findByCode(code).isPresent());
        return code;
    }
}
