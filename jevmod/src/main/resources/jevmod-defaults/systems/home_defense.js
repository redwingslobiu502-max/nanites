function onTick(ctx, config) {
    if (!ctx.blocks.connected(config.ammo_port)) return;
    if (config.require_redstone && ctx.redstone.input() === 0) return;
    var targets = ctx.entities.nearby(config.radius, config.hostiles_only);
    for (var i = 0; i < targets.length; i++) {
        if (targets[i].id === ctx.body.id()) continue;
        if (ctx.projectiles.fire(config.ammo_port, targets[i].id, config.ammunition, config.speed, config.damage)) {
            ctx.memory.set("shots", Number(ctx.memory.get("shots", 0)) + 1);
            return;
        }
    }
}
