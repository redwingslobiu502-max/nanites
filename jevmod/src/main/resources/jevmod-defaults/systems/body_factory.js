// jevmod: systems
// Opt in by changing enabled to true and connecting a named Berço port.
function onTick(ctx, config) {
    if (ctx.body.isAlive()) return;
    var ports = ctx.blocks.connections(), p = ports[config.port];
    if (!p) return;
    var args = {x:p[0], y:p[1], z:p[2]};
    var status = ctx.call("jev:core", "berco.status", args);
    if (status.ready) {
        args.bind = true;
        var released = ctx.call("jev:core", "berco.release", args);
        if (released.released) ctx.memory.set("last_body", released.body_id);
        return;
    }
    if (status.running) return;
    var spec = JSON.parse(JSON.stringify(config.spec));
    var damage = ctx.body.lastDamage();
    var previous = ctx.memory.get("adaptations", []);
    if (config.adapt_to_last_damage && damage && previous.indexOf(damage.damage_type) < 0)
        previous.push(damage.damage_type);
    spec.resistances = (spec.resistances || []).concat(previous);
    spec.metadata = spec.metadata || {};
    spec.metadata.generation = Number(ctx.memory.get("generation", 0)) + 1;
    args.spec = spec;
    ctx.call("jev:core", "berco.begin", args);
    ctx.memory.set("generation", spec.metadata.generation);
    ctx.memory.set("adaptations", previous);
}
