package dev.jevmod.api;

/** Result of {@code ctx.world.raycast(...)}: {@code type} is "block", "entity" or "miss". */
public final class Hit {
    public final String type;
    public final Vec pos;
    public final EntityRef entity;
    public final String block;
    public final double distance;

    Hit(String type, Vec pos, EntityRef entity, String block, double distance) {
        this.type = type;
        this.pos = pos;
        this.entity = entity;
        this.block = block;
        this.distance = distance;
    }

    public boolean isEntity() {
        return entity != null;
    }

    public boolean isBlock() {
        return "block".equals(type);
    }

    public boolean isMiss() {
        return "miss".equals(type);
    }
}
