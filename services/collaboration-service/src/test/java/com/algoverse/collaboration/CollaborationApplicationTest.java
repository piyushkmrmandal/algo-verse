package com.algoverse.collaboration;

import com.algoverse.collaboration.ot.OtEngine;
import com.algoverse.collaboration.ot.TextOperation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CollaborationApplicationTest {

    private final OtEngine ot = new OtEngine();

    @Test
    void transformInsertBeforeInsert() {
        // Two concurrent inserts at different positions
        TextOperation mine = TextOperation.insert(5, "X", 0);
        TextOperation committed = TextOperation.insert(2, "AB", 0);
        TextOperation result = ot.transform(mine, committed);
        assertThat(result.getPosition()).isEqualTo(7);
    }

    @Test
    void transformInsertAfterDelete() {
        // Insert at 10, concurrent delete at 3 of length 4 — insert shifts left
        TextOperation mine = TextOperation.insert(10, "Z", 0);
        TextOperation committed = TextOperation.delete(3, 4, 0);
        TextOperation result = ot.transform(mine, committed);
        assertThat(result.getPosition()).isEqualTo(6);
    }

    @Test
    void transformDeleteAgainstDelete_noOverlap() {
        TextOperation mine = TextOperation.delete(10, 3, 0);
        TextOperation committed = TextOperation.delete(2, 4, 0);
        TextOperation result = ot.transform(mine, committed);
        assertThat(result).isNotNull();
        assertThat(result.getPosition()).isEqualTo(6);
        assertThat(result.getLength()).isEqualTo(3);
    }

    @Test
    void transformDeleteAgainstDelete_fullOverlap() {
        // Both delete same range — mine becomes no-op
        TextOperation mine = TextOperation.delete(2, 4, 0);
        TextOperation committed = TextOperation.delete(2, 4, 0);
        TextOperation result = ot.transform(mine, committed);
        assertThat(result).isNull();
    }
}
