function sense() {
    var mobs = ctx.world.entitiesNear(ctx.host.pos(), 12, "hostile");
    var nearest = mobs.length > 0 ? mobs[0] : null;
    return {
        count: mobs.length,
        nearest: nearest ? {type: nearest.type(), distance: ctx.host.distanceTo(nearest.pos())} : null,
        low_health: ctx.host.health() < ctx.host.maxHealth() * 0.4
    };
}
