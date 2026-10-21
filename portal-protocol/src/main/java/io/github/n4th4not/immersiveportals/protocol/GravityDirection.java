package io.github.n4th4not.immersiveportals.protocol;

/**
 * The six gravity frames of Gravity Changer 26.2. Local positive Y points away from gravity. Basis
 * mappings adapted from NightEpiphany's RotationUtil (MIT; see licenses/GravityChanger-MIT.txt).
 */
public enum GravityDirection {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    private final Vec3 vector;

    GravityDirection(int x, int y, int z) {
        vector = new Vec3(x, y, z);
    }

    public Vec3 vector() {
        return vector;
    }

    public Vec3 toWorld(Vec3 v) {
        double x = v.x(), y = v.y(), z = v.z();
        return switch (this) {
            case DOWN -> v;
            case UP -> new Vec3(-x, -y, z);
            case NORTH -> new Vec3(x, -z, y);
            case SOUTH -> new Vec3(-x, -z, -y);
            case WEST -> new Vec3(y, -z, -x);
            case EAST -> new Vec3(-y, -z, x);
        };
    }

    public Vec3 toLocal(Vec3 v) {
        double x = v.x(), y = v.y(), z = v.z();
        return switch (this) {
            case DOWN -> v;
            case UP -> new Vec3(-x, -y, z);
            case NORTH -> new Vec3(x, z, -y);
            case SOUTH -> new Vec3(-x, -z, -y);
            case WEST -> new Vec3(-z, x, -y);
            case EAST -> new Vec3(z, -x, -y);
        };
    }

    public GravityDirection rotated(Quaternion rotation) {
        return nearest(rotation.rotate(vector));
    }

    public static GravityDirection nearest(Vec3 direction) {
        if (direction.lengthSquared() < 1e-12)
            throw new IllegalArgumentException("Zero gravity direction");
        GravityDirection result = DOWN;
        double best = Double.NEGATIVE_INFINITY;
        for (var candidate : values()) {
            double score = candidate.vector.dot(direction);
            if (score > best) {
                best = score;
                result = candidate;
            }
        }
        return result;
    }

    public static GravityDirection fromId(int id) {
        if (id < 0 || id >= values().length)
            throw new IllegalArgumentException("Invalid gravity direction");
        return values()[id];
    }
}
