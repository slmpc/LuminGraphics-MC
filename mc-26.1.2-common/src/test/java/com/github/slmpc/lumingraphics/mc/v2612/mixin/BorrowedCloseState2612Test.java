package com.github.slmpc.lumingraphics.mc.v2612.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BorrowedCloseState2612Test {
    @Test
    void borrowedReleaseRunsOnceAcrossRepeatedCloseWhileOwnedStateIsUntouched() {
        BorrowedCloseState2612 owned = new BorrowedCloseState2612();
        assertFalse(owned.borrowed());

        AtomicInteger releases = new AtomicInteger();
        BorrowedCloseState2612 borrowed = new BorrowedCloseState2612();
        borrowed.mark(releases::incrementAndGet);
        assertTrue(borrowed.borrowed());
        borrowed.releaseOnce();
        borrowed.releaseOnce();
        assertEquals(1, releases.get());
        assertThrows(IllegalStateException.class, () -> borrowed.mark(() -> { }));
    }
}
