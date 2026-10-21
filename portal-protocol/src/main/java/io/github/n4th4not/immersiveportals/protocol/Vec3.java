package io.github.n4th4not.immersiveportals.protocol;

/** Immutable, finite coordinates shared by the client and server library. */
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Coordinates must be finite");
    }

    public Vec3 add(Vec3 v) {
        return new Vec3(x + v.x, y + v.y, z + v.z);
    }

    public Vec3 subtract(Vec3 v) {
        return new Vec3(x - v.x, y - v.y, z - v.z);
    }

    public Vec3 multiply(double k) {
        return new Vec3(x * k, y * k, z * k);
    }

    public double dot(Vec3 v) {
        return x * v.x + y * v.y + z * v.z;
    }

    public Vec3 cross(Vec3 v) {
        return new Vec3(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y * v.x);
    }

    public double lengthSquared() {
        return dot(this);
    }

    public Vec3 normalized() {
        double n = Math.sqrt(lengthSquared());
        if (n < 1e-12) throw new IllegalArgumentException("Zero direction");
        return multiply(1 / n);
    }
}
