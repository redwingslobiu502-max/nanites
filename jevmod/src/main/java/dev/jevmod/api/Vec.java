package dev.jevmod.api;

import com.google.gson.JsonObject;
import dev.jevmod.core.Json;
import dev.jevmod.core.script.JsonSerializableApi;
import net.minecraft.world.phys.Vec3;

/** Immutable 3D vector for scripts: {@code v.x}, {@code v.add(o)}, {@code v.scale(2)}, {@code v.normalize()}... */
public final class Vec implements JsonSerializableApi {
    public final double x;
    public final double y;
    public final double z;

    public Vec(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Vec of(Vec3 v) {
        return new Vec(v.x, v.y, v.z);
    }

    public Vec3 mc() {
        return new Vec3(x, y, z);
    }

    public Vec add(Vec o) {
        return new Vec(x + o.x, y + o.y, z + o.z);
    }

    public Vec add(double dx, double dy, double dz) {
        return new Vec(x + dx, y + dy, z + dz);
    }

    public Vec sub(Vec o) {
        return new Vec(x - o.x, y - o.y, z - o.z);
    }

    public Vec scale(double s) {
        return new Vec(x * s, y * s, z * s);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public Vec normalize() {
        double l = length();
        return l < 1e-9 ? new Vec(0, 0, 0) : scale(1 / l);
    }

    public double dot(Vec o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vec cross(Vec o) {
        return new Vec(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double distance(Vec o) {
        return sub(o).length();
    }

    /** Linear interpolation towards {@code o} (t = 0 -> this, t = 1 -> o). */
    public Vec lerp(Vec o, double t) {
        return new Vec(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
    }

    /** Rotates around the vertical axis by {@code degrees}. */
    public Vec rotateY(double degrees) {
        double r = Math.toRadians(degrees), c = Math.cos(r), s = Math.sin(r);
        return new Vec(x * c - z * s, y, x * s + z * c);
    }

    @Override
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.add("x", Json.round(x, 1));
        o.add("y", Json.round(y, 1));
        o.add("z", Json.round(z, 1));
        return o;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "(%.2f, %.2f, %.2f)", x, y, z);
    }
}
