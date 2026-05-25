package com.algoverse.collaboration.ot;

import org.springframework.stereotype.Component;

/**
 * Server-side Operational Transform engine (inclusion transform).
 *
 * Transform rules (standard OT for plain text):
 *   T(op1, op2) = op1 adjusted so it can be applied after op2 has already been applied.
 */
@Component
public class OtEngine {

    /**
     * Transform {@code incoming} against {@code committed} (an op that was already applied).
     * Returns the adjusted version of {@code incoming} that produces the correct result
     * when applied to the document state after {@code committed}.
     */
    public TextOperation transform(TextOperation incoming, TextOperation committed) {
        return switch (incoming.getType()) {
            case INSERT -> transformInsert(incoming, committed);
            case DELETE -> transformDelete(incoming, committed);
        };
    }

    private TextOperation transformInsert(TextOperation ins, TextOperation committed) {
        int pos = ins.getPosition();

        if (committed.getType() == TextOperation.Type.INSERT) {
            int cPos = committed.getPosition();
            // Tiebreak: if both insert at same position, committed wins — push incoming right
            if (cPos < pos || (cPos == pos)) {
                pos += committed.getText().length();
            }
        } else { // committed is DELETE
            int cPos = committed.getPosition();
            int cLen = committed.getLength();
            if (cPos < pos) {
                pos = Math.max(cPos, pos - cLen);
            }
        }

        return TextOperation.insert(pos, ins.getText(), ins.getClientVersion());
    }

    private TextOperation transformDelete(TextOperation del, TextOperation committed) {
        int pos = del.getPosition();
        int len = del.getLength();

        if (committed.getType() == TextOperation.Type.INSERT) {
            int cPos = committed.getPosition();
            int cLen = committed.getText().length();
            if (cPos <= pos) {
                pos += cLen;
            } else if (cPos < pos + len) {
                // committed insert lands inside our delete range — extend delete
                len += cLen;
            }
        } else { // committed is DELETE
            int cPos = committed.getPosition();
            int cLen = committed.getLength();

            if (cPos + cLen <= pos) {
                // committed delete is entirely before our range
                pos -= cLen;
            } else if (cPos >= pos + len) {
                // committed delete is entirely after our range — no change
            } else {
                // overlapping deletes — shrink our delete by the overlap
                int overlapStart = Math.max(pos, cPos);
                int overlapEnd = Math.min(pos + len, cPos + cLen);
                len -= (overlapEnd - overlapStart);
                pos = Math.min(pos, cPos);
            }
        }

        if (len <= 0) return null; // operation became a no-op
        return TextOperation.delete(pos, len, del.getClientVersion());
    }
}
