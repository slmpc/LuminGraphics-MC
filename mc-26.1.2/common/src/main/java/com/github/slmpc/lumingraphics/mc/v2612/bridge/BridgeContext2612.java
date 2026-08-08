package com.github.slmpc.lumingraphics.mc.v2612.bridge;

import com.github.slmpc.prismrhi.context.PRhiContextIdentity;
import com.github.slmpc.prismrhi.context.PRhiInvalidationToken;

interface BridgeContext2612 {
    void requireCurrent();
    PRhiContextIdentity identity();
    PRhiInvalidationToken invalidation();
}
