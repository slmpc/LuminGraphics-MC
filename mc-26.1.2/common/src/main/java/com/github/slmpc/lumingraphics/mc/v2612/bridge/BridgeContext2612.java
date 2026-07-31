package com.github.slmpc.lumingraphics.mc.v2612.bridge;

import com.github.slmpc.prismrhi.context.RhiContextIdentity;
import com.github.slmpc.prismrhi.context.RhiInvalidationToken;

interface BridgeContext2612 {
    void requireCurrent();
    RhiContextIdentity identity();
    RhiInvalidationToken invalidation();
}
