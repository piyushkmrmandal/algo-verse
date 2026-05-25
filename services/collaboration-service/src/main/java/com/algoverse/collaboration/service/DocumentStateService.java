package com.algoverse.collaboration.service;

import com.algoverse.collaboration.ot.TextOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Stores the live document text and version counter in Redis.
 * The authoritative document lives here until the room ends,
 * at which point it is flushed to a CrdtSnapshot.
 */
@Service
@RequiredArgsConstructor
public class DocumentStateService {

    private static final String CONTENT_KEY = "collab:doc:content:";
    private static final String VERSION_KEY  = "collab:doc:version:";
    private static final Duration DOC_TTL = Duration.ofHours(4);

    private final RedisTemplate<String, Object> redis;

    public String getContent(UUID roomId) {
        Object val = redis.opsForValue().get(CONTENT_KEY + roomId);
        return val != null ? val.toString() : "";
    }

    public long getVersion(UUID roomId) {
        Object val = redis.opsForValue().get(VERSION_KEY + roomId);
        return val != null ? Long.parseLong(val.toString()) : 0L;
    }

    public void initDocument(UUID roomId, String initialContent) {
        redis.opsForValue().set(CONTENT_KEY + roomId, initialContent, DOC_TTL);
        redis.opsForValue().set(VERSION_KEY + roomId, "0", DOC_TTL);
    }

    public synchronized long applyOperation(UUID roomId, TextOperation op) {
        String content = getContent(roomId);
        String newContent = applyToText(content, op);
        long newVersion = getVersion(roomId) + 1;
        redis.opsForValue().set(CONTENT_KEY + roomId, newContent, DOC_TTL);
        redis.opsForValue().set(VERSION_KEY + roomId, String.valueOf(newVersion), DOC_TTL);
        return newVersion;
    }

    public void clearDocument(UUID roomId) {
        redis.delete(CONTENT_KEY + roomId);
        redis.delete(VERSION_KEY + roomId);
    }

    private String applyToText(String doc, TextOperation op) {
        if (op == null) return doc;
        int pos = Math.max(0, Math.min(op.getPosition(), doc.length()));
        return switch (op.getType()) {
            case INSERT -> doc.substring(0, pos) + op.getText() + doc.substring(pos);
            case DELETE -> {
                int end = Math.min(pos + op.getLength(), doc.length());
                yield doc.substring(0, pos) + doc.substring(end);
            }
        };
    }
}
