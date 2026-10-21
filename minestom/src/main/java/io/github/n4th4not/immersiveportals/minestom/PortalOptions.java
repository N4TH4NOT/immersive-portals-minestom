package io.github.n4th4not.immersiveportals.minestom;

import io.github.n4th4not.immersiveportals.protocol.*;

import net.kyori.adventure.nbt.*;
import net.minestom.server.network.NetworkBuffer;

import java.util.*;

/**
 * Typed helpers for native portal options; arbitrary native NBT remains available for advanced use.
 */
public record PortalOptions(CompoundBinaryTag nbt) {
    public PortalOptions {
        Objects.requireNonNull(nbt);
    }

    public static Builder builder() {
        return new Builder();
    }

    public byte[] encode() {
        return NetworkBuffer.makeArray(NetworkBuffer.NBT, nbt);
    }

    public static PortalOptions of(PortalDefinition portal) {
        byte[] bytes = portal.extraNbt();
        if (bytes.length == 0) return new PortalOptions(CompoundBinaryTag.empty());
        var buffer = NetworkBuffer.wrap(bytes, 0, bytes.length);
        var tag = buffer.read(NetworkBuffer.NBT);
        if (!(tag instanceof CompoundBinaryTag compound) || buffer.readableBytes() != 0)
            throw new IllegalArgumentException("Portal options must contain one compound NBT tag");
        return new PortalOptions(compound);
    }

    public boolean teleportable() {
        return nbt.getBoolean("teleportable", true);
    }

    public boolean interactable() {
        return nbt.getBoolean("interactable", true);
    }

    public boolean changesScale() {
        return nbt.getBoolean("teleportChangesScale", true);
    }

    /** First entry (or exit for an inward box) through the actual aperture. */
    public double intersection(PortalDefinition portal, Vec3 from, Vec3 to) {
        var shape = nbt.getCompound("portalShape");
        if (!shape.getString("type").equals("box")) {
            double t = portal.intersection(from, to);
            return Double.isFinite(t) && contains(portal, from.add(to.subtract(from).multiply(t)))
                    ? t
                    : Double.NaN;
        }
        double thickness = nbt.getDouble("thickness");
        if (!Double.isFinite(thickness) || thickness <= 0) return Double.NaN;
        Vec3 a = from.subtract(portal.origin()), b = to.subtract(from), normal = portal.normal();
        double[] origin = {a.dot(portal.axisW()), a.dot(portal.axisH()), a.dot(normal)};
        double[] direction = {b.dot(portal.axisW()), b.dot(portal.axisH()), b.dot(normal)};
        double[] extent = {portal.width() / 2, portal.height() / 2, thickness / 2};
        double enter = Double.NEGATIVE_INFINITY, exit = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(direction[i]) < 1e-12) {
                if (Math.abs(origin[i]) > extent[i]) return Double.NaN;
                continue;
            }
            double t1 = (-extent[i] - origin[i]) / direction[i],
                    t2 = (extent[i] - origin[i]) / direction[i];
            enter = Math.max(enter, Math.min(t1, t2));
            exit = Math.min(exit, Math.max(t1, t2));
            if (enter > exit) return Double.NaN;
        }
        double t = shape.getBoolean("facingOutwards") ? enter : exit;
        return t >= 0 && t <= 1 ? t : Double.NaN;
    }

    public boolean contains(PortalDefinition portal, Vec3 point) {
        CompoundBinaryTag shape = nbt.getCompound("portalShape");
        if (!shape.getString("type").equals("specialFlat")) return true;
        CompoundBinaryTag mesh = shape.getCompound("shape");
        var points = mesh.getList("pointCoords");
        var triangles = mesh.getList("triangles");
        Vec3 local = point.subtract(portal.origin());
        double x = local.dot(portal.axisW()) / (portal.width() / 2),
                y = local.dot(portal.axisH()) / (portal.height() / 2);
        for (int i = 0; i + 2 < triangles.size(); i += 3) {
            int a = triangles.getInt(i), b = triangles.getInt(i + 1), c = triangles.getInt(i + 2);
            if (a < 0 || b < 0 || c < 0 || Math.max(a, Math.max(b, c)) * 2 + 1 >= points.size())
                continue;
            double ax = points.getDouble(a * 2),
                    ay = points.getDouble(a * 2 + 1),
                    bx = points.getDouble(b * 2),
                    by = points.getDouble(b * 2 + 1),
                    cx = points.getDouble(c * 2),
                    cy = points.getDouble(c * 2 + 1);
            double area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
            if (!Double.isFinite(area) || Math.abs(area) < 1e-12) continue;
            double d1 = (x - bx) * (ay - by) - (ax - bx) * (y - by),
                    d2 = (x - cx) * (by - cy) - (bx - cx) * (y - cy),
                    d3 = (x - ax) * (cy - ay) - (cx - ax) * (y - ay);
            if (!((d1 < 0 || d2 < 0 || d3 < 0) && (d1 > 0 || d2 > 0 || d3 > 0))) return true;
        }
        return false;
    }

    public static final class Builder {
        private final CompoundBinaryTag.Builder tag = CompoundBinaryTag.builder();

        public Builder teleportable(boolean v) {
            tag.putBoolean("teleportable", v);
            return this;
        }

        public Builder interactable(boolean v) {
            tag.putBoolean("interactable", v);
            return this;
        }

        public Builder visible(boolean v) {
            tag.putBoolean("isVisible", v);
            return this;
        }

        public Builder renderPlayer(boolean v) {
            tag.putBoolean("doRenderPlayer", v);
            return this;
        }

        public Builder changesScale(boolean v) {
            tag.putBoolean("teleportChangesScale", v);
            return this;
        }

        public Builder crossPortalCollision(boolean v) {
            tag.putBoolean("hasCrossPortalCollision", v);
            return this;
        }

        public Builder fuseView(boolean v) {
            tag.putBoolean("fuseView", v);
            return this;
        }

        public Builder box(double thickness, boolean facingOutwards) {
            if (!Double.isFinite(thickness) || thickness <= 0 || thickness > 30_000_000)
                throw new IllegalArgumentException("Invalid box thickness");
            tag.putDouble("thickness", thickness)
                    .put(
                            "portalShape",
                            CompoundBinaryTag.builder()
                                    .putString("type", "box")
                                    .putBoolean("facingOutwards", facingOutwards)
                                    .build());
            return this;
        }

        public Builder nativeOptions(CompoundBinaryTag value) {
            tag.put(value);
            return this;
        }

        /** Normalized coordinates in [-1,1], six numbers per triangle. */
        public Builder triangles(double... coordinates) {
            if (coordinates.length == 0 || coordinates.length % 6 != 0 || coordinates.length > 6000)
                throw new IllegalArgumentException("Invalid triangle mesh");
            for (int i = 0; i < coordinates.length; i += 6) {
                double area =
                        (coordinates[i + 2] - coordinates[i])
                                        * (coordinates[i + 5] - coordinates[i + 1])
                                - (coordinates[i + 3] - coordinates[i + 1])
                                        * (coordinates[i + 4] - coordinates[i]);
                if (!Double.isFinite(area) || Math.abs(area) < 1e-12)
                    throw new IllegalArgumentException("Degenerate triangle");
            }
            var points = ListBinaryTag.builder(BinaryTagTypes.DOUBLE);
            var indices = ListBinaryTag.builder(BinaryTagTypes.INT);
            for (int i = 0; i < coordinates.length; i++) {
                if (!Double.isFinite(coordinates[i]) || Math.abs(coordinates[i]) > 1)
                    throw new IllegalArgumentException("Mesh coordinates must be in [-1,1]");
                points.add(DoubleBinaryTag.doubleBinaryTag(coordinates[i]));
                if (i % 2 == 0) indices.add(IntBinaryTag.intBinaryTag(i / 2));
            }
            var mesh =
                    CompoundBinaryTag.builder()
                            .put("pointCoords", points.build())
                            .put("triangles", indices.build())
                            .build();
            tag.put(
                    "portalShape",
                    CompoundBinaryTag.builder()
                            .putString("type", "specialFlat")
                            .put("shape", mesh)
                            .build());
            return this;
        }

        public PortalOptions build() {
            return new PortalOptions(tag.build());
        }
    }
}
