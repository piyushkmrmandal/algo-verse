package com.algoverse.collaboration.ot;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An immutable text edit. Position is in UTF-16 code units (matches JS string indexing).
 * INSERT ops carry text; DELETE ops carry length.
 * clientVersion is the document version the client saw when it produced this operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextOperation {

    public enum Type { INSERT, DELETE }

    private Type type;
    private int position;
    private String text;    // used for INSERT
    private int length;     // used for DELETE
    private long clientVersion;

    public static TextOperation insert(int position, String text, long clientVersion) {
        return TextOperation.builder()
                .type(Type.INSERT).position(position).text(text)
                .clientVersion(clientVersion).build();
    }

    public static TextOperation delete(int position, int length, long clientVersion) {
        return TextOperation.builder()
                .type(Type.DELETE).position(position).length(length)
                .clientVersion(clientVersion).build();
    }

    public int affectedLength() {
        return type == Type.INSERT ? text.length() : length;
    }
}
