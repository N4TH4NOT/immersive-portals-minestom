package qouteall.imm_ptl.core.render;

import java.util.concurrent.atomic.LongAdder;

/** Opt-in counters for graphics regression scenarios; no atomic operations in normal play. */
public final class RenderDiagnostics {
    private static final boolean ENABLED = Boolean.getBoolean("imm_ptl.renderDiagnostics");
    private static final LongAdder TESTS = new LongAdder(),
            REJECTED = new LongAdder(),
            SODIUM = new LongAdder();

    public static void frustum(boolean rejected) {
        if (ENABLED) {
            TESTS.increment();
            if (rejected) REJECTED.increment();
        }
    }

    public static void sodiumViewport() {
        if (ENABLED) SODIUM.increment();
    }

    public static long tests() {
        return TESTS.sum();
    }

    public static long rejected() {
        return REJECTED.sum();
    }

    public static long sodiumViewports() {
        return SODIUM.sum();
    }
}
