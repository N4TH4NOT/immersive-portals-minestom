package io.github.n4th4not.immersiveportals.protocol;

public record Quaternion(double x, double y, double z, double w) {
    public static final Quaternion IDENTITY = new Quaternion(0, 0, 0, 1);

    public Quaternion {
        double n = x * x + y * y + z * z + w * w;
        if (!Double.isFinite(n) || Math.abs(n - 1) > 1e-6)
            throw new IllegalArgumentException("Rotation must be a unit quaternion");
    }

    public static Quaternion axisAngle(Vec3 axis, double radians) {
        Vec3 v = axis.normalized().multiply(Math.sin(radians / 2));
        return new Quaternion(v.x(), v.y(), v.z(), Math.cos(radians / 2));
    }

    public Quaternion inverse() {
        return new Quaternion(-x, -y, -z, w);
    }

    public Vec3 rotate(Vec3 v) {
        Vec3 q = new Vec3(x, y, z), t = q.cross(v).multiply(2);
        return v.add(t.multiply(w)).add(q.cross(t));
    }
}
