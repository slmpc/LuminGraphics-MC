package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.slmpc.prismrhi.context.RhiInvalidationToken;
import org.junit.jupiter.api.Test;

class ExternalContext2612CharacterizationTest {
    @Test
    void existingExternalContextTokenInvalidatesOnceAndRejectsUse() {
        RhiInvalidationToken token = new RhiInvalidationToken();

        assertTrue(token.isValid());
        assertTrue(token.invalidate());
        assertFalse(token.invalidate());
        assertFalse(token.isValid());
        assertThrows(RuntimeException.class, token::requireValid);
        token.close();
    }
}
