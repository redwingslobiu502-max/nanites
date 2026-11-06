function onTick(ctx, config) {
    if (!ctx.blocks.connected(config.fuel_port)) return;
    if (config.require_redstone && ctx.redstone.input() === 0) return;
    var charge = Number(ctx.memory.readSystem("energy", "charge", 0));
    if (charge > config.refuel_below) return;
    if (ctx.blocks.port(config.fuel_port).consume(config.fuel, 1) === 1) {
        engineering.charge(ctx, config.energy_per_fuel, config.capacity);
        ctx.memory.set("burned", Number(ctx.memory.get("burned", 0)) + 1);
    }
}
