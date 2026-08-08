package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.slmpc.prismrhi.context.PRhiInvalidationToken;
import org.junit.jupiter.api.Test;

class ExternalContext2612CharacterizationTest {
    @Test
    void existingExternalContextTokenInvalidatesOnceAndRejectsUse() {
        PRhiInvalidationToken token = new PRhiInvalidationToken();

        assertTrue(token.isValid());
        assertTrue(token.invalidate());
        assertFalse(token.invalidate());
        assertFalse(token.isValid());
        assertThrows(RuntimeException.class, token::requireValid);
        token.close();
    }
}
