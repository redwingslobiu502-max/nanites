function onStart(ctx, config) {
    ctx.memory.set("charge", engineering.battery(ctx, config.initial, config.capacity));
}
function onTick(ctx, config) {
    if (!ctx.body.exists()) return;
    if (ctx.memory.readSystem("assistant", "requested_halt", false)) { ctx.body.halt(); return; }
    var charge = engineering.battery(ctx, config.initial, config.capacity);
    if (charge >= config.drain_per_tick && charge > 0) {
        ctx.memory.set("charge", Math.max(0, charge - config.drain_per_tick));
        if (charge - config.drain_per_tick > 0) ctx.body.resume(); else ctx.body.halt();
    } else {
        ctx.memory.set("charge", 0);
        ctx.body.halt();
    }
}
function onStop(ctx) { if (ctx.body.exists()) ctx.body.halt(); }
