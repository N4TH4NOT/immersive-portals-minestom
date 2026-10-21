package io.github.n4th4not.immersiveportals.protocol;

import java.util.Objects;

/** Direction and strength in the units used by Gravity Changer 26.2. */
public record GravityState(GravityDirection direction, double strength) {
    public static final double DEFAULT_STRENGTH = 9.8;
    public static final GravityState DEFAULT =
            new GravityState(GravityDirection.DOWN, DEFAULT_STRENGTH);

    public GravityState {
        Objects.requireNonNull(direction);
        if (!Double.isFinite(strength) || strength < 5 || strength > 15)
            throw new IllegalArgumentException("Gravity strength must be between 5 and 15");
    }

    public GravityState(GravityDirection direction) {
        this(direction, DEFAULT_STRENGTH);
    }

    public GravityState rotated(Quaternion rotation) {
        return new GravityState(direction.rotated(rotation), strength);
    }
}
